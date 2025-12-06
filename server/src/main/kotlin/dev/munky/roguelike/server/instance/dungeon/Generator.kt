package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.instance.dungeon.roomset.JigsawConnection
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.interact.Region
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.CoordConversion
import dev.munky.roguelike.common.WeightedRandomList
import java.util.Random
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.LinkedList

interface Generator {
    suspend fun plan() : Result

    sealed interface Result {
        data class Success(val room: PlannedRoom) : Result
        enum class Failure : Result {
            EXCEEDED_DEPTH,
            NO_VALID_CONNECTION,
            NO_POOL,
        }
    }
}

data class PlannedRoom(
    val blueprint: RoomBlueprint,
    val position: BlockVec,
    val rotation: Rotation,
    val bounds: Region,
    val connections: MutableMap<JigsawConnection, PlannedRoom?>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlannedRoom) return false

        if (blueprint != other.blueprint) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blueprint.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }
}
/**
 * Planning-only dungeon generator that builds a graph of rooms using bounds checks
 * and backtracking, without modifying the world. The resulting [PlannedRoom] graph
 * can be committed/pasted by the Dungeon later.
 */
class BackTrackingGenerator(
    private val roomset: RoomSet,
    private val maxDepth: Int,
    private val seed: Long = System.nanoTime(),
    private val debug: Boolean = false,
    private val minY: Int = -64,
    private val maxY: Int = 319
) : Generator {
    val random = Random(seed)

    // Transient spatial index: chunk index -> set of planned bounds
    private val spatialPlan = HashMap<Long, LinkedList<PlannedRoom>>()

    override suspend fun plan(): Generator.Result {
        logDebug { "plan(): start — root='${roomset.rootRoomId}', maxDepth=$maxDepth, seed=$seed" }
        val rootBp = roomset.rooms[roomset.rootRoomId]
            ?: error("No root room '${roomset.rootRoomId}' defined.")

        // Place root at origin with a default rotation (match previous behavior)
        val rootRotation = Rotation.CLOCKWISE_90
        val rootBounds = rootBp.regionBy(BlockVec.ZERO, rootRotation)

        val root = PlannedRoom(
            blueprint = rootBp,
            position = BlockVec.ZERO,
            rotation = rootRotation,
            bounds = rootBounds,
            connections = rootBp.connectionsBy(rootRotation).associateWithTo(LinkedHashMap()) { null }
        )
        index(containedChunksOf(rootBounds), root)
        logDebug {
            "plan(): placed root room '${rootBp.id}' at ${BlockVec.ZERO} rot=$rootRotation; openConns=${root.connections.size}; spatialBuckets=${spatialPlan.size}"
        }

        return generate(root, maxDepth)
    }

    private suspend fun generate(room: PlannedRoom, depth: Int): Generator.Result {
        // We must satisfy ALL open connections of this room (and their descendants)
        if (depth < 0) {
            logDebug { "generate(): exceeded depth; room='${room.blueprint.id}' pos=${room.position}" }
            return Generator.Result.Failure.EXCEEDED_DEPTH
        }

        val open = room.connections.keys.filter { room.connections[it] == null }
        if (open.isEmpty()) {
            return Generator.Result.Success(room)
        }

        val conn = pickNextConnector(room)
        val pool = roomset.pools[conn.pool]?.takeIf { !it.isEmpty() } ?: run {
            logDebug { "generate(): NO_POOL for pool='${conn.pool}'" }
            return Generator.Result.Failure.NO_POOL
        }

        return satisfyConnection(room, depth, conn, pool)
    }

    private suspend fun satisfyConnection(
        room: PlannedRoom,
        depth: Int,
        conn: JigsawConnection,
        pool: WeightedRandomList<String>
    ): Generator.Result {
        // Build a read-only snapshot of the spatial plan for worker threads
        val snapshot = buildMap(spatialPlan.size) {
            for ((k, v) in spatialPlan) put(k, LinkedList(v))
        }

        val candidates = computeCandidates(room, conn, pool, snapshot)
        if (candidates.isEmpty()) {
            logDebug { "satisfyConnection(): NO_VALID_CONNECTION - no candidates fit geometry" }
            return Generator.Result.Failure.NO_VALID_CONNECTION
        }

        for (candidateData in candidates) {
            val candidate = PlannedRoom(
                blueprint = candidateData.bp,
                position = candidateData.pos,
                rotation = candidateData.rot,
                bounds = candidateData.region,
                connections = candidateData.allConns.associateWithTo(LinkedHashMap()) { null }
            )

            room.connections[conn] = candidate
            candidate.connections[candidateData.matchedConn] = room
            index(candidateData.chunks, candidate)

            logDebug { "satisfyConnection(): descending into ${candidate.blueprint.id}." }
            val branchResult = if (generate(candidate, depth - 1) is Generator.Result.Success) {
                generate(room, depth)
            } else {
                Generator.Result.Failure.NO_VALID_CONNECTION
            }

            if (branchResult is Generator.Result.Success) {
                return Generator.Result.Success(room)
            }

            logDebug { "satisfyConnection(): back tracking ${candidate.blueprint.id}." }
            unindex(candidateData.chunks, candidate)
            room.connections[conn] = null
            candidate.connections[candidateData.matchedConn] = null
        }

        logDebug { "satisfyConnection(): All candidates exhausted for this connection." }
        return Generator.Result.Failure.NO_VALID_CONNECTION
    }

    private fun pickNextConnector(room: PlannedRoom): JigsawConnection {
        val open = room.connections.keys.filter { room.connections[it] == null }
        return open.minBy { conn ->
            val pool = roomset.pools[conn.pool]
            // smaller pool → more constrained → try first
            pool?.size ?: Int.MAX_VALUE
        }
    }

    private fun computeCandidatePosition(
        room: PlannedRoom,
        conn: JigsawConnection,
        candidateConn: JigsawConnection
    ): BlockVec {
        val connectorPos = conn.position.add(room.position)
        val otherPos = connectorPos.add(
            conn.direction.normalX(),
            conn.direction.normalY(),
            conn.direction.normalZ()
        )
        return otherPos.sub(candidateConn.position)
    }

    private fun index(chunks: LongArray, pr: PlannedRoom) {
        for (chunk in chunks) {
            spatialPlan.getOrPut(chunk, ::LinkedList).add(pr)
        }
    }

    private fun unindex(chunks: LongArray, pr: PlannedRoom) {
        for (chunk in chunks) {
            spatialPlan[chunk]?.remove(pr)
        }
    }

    // Read-only intersection check that uses a snapshot instead of the live mutable map
    private fun isIntersectingSnapshot(
        chunks: LongArray,
        candidate: Region,
        snap: Map<Long, LinkedList<PlannedRoom>>
    ): Boolean {
        for (c in chunks) {
            val possible = snap[c] ?: continue
            for (r in possible) if (r.bounds.intersectsAabb(candidate)) return true
        }
        return false
    }

    // Result container passed from worker to owner
    private class CandidateResult(
        val bp: RoomBlueprint,
        val rot: Rotation,
        val pos: BlockVec,
        val region: Region,
        val allConns: List<JigsawConnection>,
        val matchedConn: JigsawConnection,
        val chunks: LongArray
    )

    // Owner spawns workers on Default dispatcher to evaluate candidates against snapshot
    private suspend fun computeCandidates(
        owner: PlannedRoom,
        conn: JigsawConnection,
        pool: WeightedRandomList<String>,
        snapshot: Map<Long, LinkedList<PlannedRoom>>
    ): List<CandidateResult> = coroutineScope {

        val maxRooms = pool.size
        val sampled = LinkedHashSet<String>(maxRooms)
        // Currently samples every room in the pool to evaluate at the same time
        while (sampled.size < maxRooms) {
            sampled += pool.weightedRandom(random)
        }
        val tasks = ArrayList<kotlinx.coroutines.Deferred<CandidateResult?>>()
        val inverted = conn.direction.opposite()
        for (roomId in sampled) {
            val bp = roomset.rooms[roomId] ?: continue
            for (rot in Rotation.entries) {
                val candidateConnections = bp.connectionsBy(rot)
                for (c in candidateConnections) {
                    if (c.pool != conn.pool) continue
                    if (c.direction != inverted) continue
                    tasks += async(Dispatchers.Default) {
                        val candidatePos = computeCandidatePosition(owner, conn, c)
                        val candidateRegion = bp.regionBy(candidatePos, rot)
                        val candidateBounds = candidateRegion.expand(-0.1)
                        val candidateChunks = containedChunksOf(candidateBounds)
                        if (!isWithinHeight(candidateBounds)) return@async null
                        if (isIntersectingSnapshot(candidateChunks, candidateBounds, snapshot)) return@async null
                        CandidateResult(
                            bp = bp,
                            rot = rot,
                            pos = candidatePos,
                            region = candidateRegion,
                            allConns = candidateConnections,
                            matchedConn = c,
                            chunks = candidateChunks
                        )
                    }
                }
            }
        }

        if (tasks.isEmpty()) return@coroutineScope emptyList()
        tasks.awaitAll().filterNotNull()
    }

    private fun containedChunksOf(area: Region): LongArray {
        val min = area.boundingBox.min
        val max = area.boundingBox.max

        val minChunkX = CoordConversion.globalToChunk(floor(min.x()).toInt())
        val minChunkZ = CoordConversion.globalToChunk(floor(min.z()).toInt())
        val maxChunkX = CoordConversion.globalToChunk(ceil(max.x()).toInt())
        val maxChunkZ = CoordConversion.globalToChunk(ceil(max.z()).toInt())

        val chunksX = maxChunkX - minChunkX + 1
        val chunksZ = maxChunkZ - minChunkZ + 1

        val result = LongArray(chunksX * chunksZ)
        var i = 0
        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                result[i++] = CoordConversion.chunkIndex(x, z)
            }
        }
        return result
    }

    private fun isWithinHeight(area: Region): Boolean {
        val bb = area.boundingBox
        val minYd = bb.min.y()
        val maxYd = bb.max.y()
        return minYd >= minY && maxY >= maxYd
    }

    companion object {
        val LOGGER = logger {}
    }

    // Debug logging helper to avoid string building when disabled
    private inline fun logDebug(message: () -> String) {
        if (debug) LOGGER.debug(message())
    }
}