package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.instance.dungeon.roomset.JigsawConnection
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.interact.Region
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import dev.munky.roguelike.common.WeightedRandomList
import dev.munky.roguelike.server.instance.dungeon.roomset.Pool
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import kotlinx.coroutines.Deferred
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.LinkedList
import java.util.TreeMap
import kotlin.math.E
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

interface Generator {
    suspend fun plan() : Result

    sealed interface Result {
        data class Success(val room: PlannedRoom) : Result
        enum class Failure : Result {
            DEPTH_EXCEEDED,
            NO_VALID_CONNECTION,
            NO_POOL,
            EMPTY_POOL,
            TOO_SMALL,
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

    override fun toString(): String {
        return "PlannedRoom(bounds=$bounds, rotation=$rotation, position=$position, blueprint=$blueprint, connections=${connections.entries.joinToString { "${it.key.name}: ${it.value?.blueprint?.id}" }})"
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
    var stats = Stats()

    data class Stats(
        var roomsPlanned: Long = 0L,
        var chunksChecked: Long = 0L,

        var descends: Long = 0L,
        var ascends: Long = 0L,

        var intersectionsChecked: Long = 0L,
        var intersectionsFailed: Long = 0L,

        var candidatesTried: Long = 0L,
        var spatialBinAccesses: Long = 0L,
        var spatialBinCopies: Long = 0L,
        var spatialBinAverageCopySize: Double = .0,
        var spatialBinMaxSize: Long = 0L,

        var heightBoundsFails: Long = 0L,
        var timeTaken: Duration = 0.milliseconds,
    )

    val random = Random(seed)

    // Transient spatial index: chunk index -> set of planned bounds
    private val spatialBin = TreeMap<Long, LinkedList<PlannedRoom>>()

    override suspend fun plan(): Generator.Result {
        val start = TimeSource.Monotonic.markNow()
        LOGGER.info("Starting generator for roomset '${roomset.id}'.")
        logDebug { "plan(): maxDepth=$maxDepth, seed=$seed" }
        val rootBp = roomset.rooms[roomset.rootRoomId]
            ?: error("No root room '${roomset.rootRoomId}' defined.")

        // Place root at origin with a default rotation (match previous behavior)
        val rootRotation = Rotation.CLOCKWISE_90
        val rootBounds = rootBp.regionAt(BlockVec(0, 100, 0), rootRotation)

        val root = PlannedRoom(
            blueprint = rootBp,
            position = BlockVec(0, 100, 0),
            rotation = rootRotation,
            bounds = rootBounds,
            connections = rootBp.connectionsWith(rootRotation).associateWithTo(LinkedHashMap()) { null }
        )

        index(rootBounds.containedChunks(), root)
        logDebug {
            "plan(): placed root room '${rootBp.id}' at ${BlockVec.ZERO} rot=$rootRotation; openConns=${root.connections.size}; spatialBuckets=${spatialBin.size}"
        }

        var tries = 20
        while (tries-- > 0) {
            when (val r = generate(root, 0)) {
                is Generator.Result.Success -> {
                    stats.timeTaken = TimeSource.Monotonic.markNow().minus(start)
                    return r
                }
                is Generator.Result.Failure -> {
                    // reset existing connections, in case it generated an impossible situation.
                    for (c in root.connections.keys) {
                        root.connections[c] = null
                    }
                    spatialBin.clear()
                    stats.roomsPlanned = 0
                    LOGGER.warn("Could not generate connection at root (${r.name}), $tries left.")
                }
            }
        }
        stats.timeTaken = TimeSource.Monotonic.markNow().minus(start)
        return Generator.Result.Failure.NO_VALID_CONNECTION
    }

    /**
     * Generate a room for a connection on a room.
     */
    private suspend fun generate(room: PlannedRoom, depth: Int): Generator.Result {
        // We must satisfy ALL open connections of this room (and their descendants)
        if (depth >= maxDepth) {
            logDebug { "generate(): exceeded depth; room='${room.blueprint.id}' pos=${room.position}" }
            return Generator.Result.Failure.DEPTH_EXCEEDED
        }

        // All connections satisfied, this room is complete.
        if (null !in room.connections.values) {
            if (room.connections.values.size > 1) return Generator.Result.Success(room)
            val heuristic = depthHeuristic(depth)
            val chance = random.nextDouble()
            // Dungeon too small, try another room.
            return if (heuristic > chance) Generator.Result.Failure.TOO_SMALL
            else Generator.Result.Success(room)
        }

        val conn = pickNextConnector(room)
        val pool = conn.pool ?: run {
            logDebug { "generate(): NO_POOL for pool='${conn.pool}'" }
            return Generator.Result.Failure.NO_POOL
        }

        if (pool.rooms.isEmpty()) {
            logDebug { "generate(): EMPTY_POOL for pool='${conn.pool}'" }
            return Generator.Result.Failure.EMPTY_POOL
        }

        return satisfyConnection(room, depth, conn, pool)
    }

    private fun depthHeuristic(depth: Int) : Double {
        val t = 10.0
        val ramp = 1 - (depth / maxDepth.toDouble())
        val num = 1 - E.pow(t * ramp)
        val den = 1 - E.pow(t)
        val allowable = 0.9
        return (num / den) * allowable
    }

    /**
     * Satisfy the connection of a room.
     */
    private suspend fun satisfyConnection(
        room: PlannedRoom,
        depth: Int,
        conn: JigsawConnection,
        pool: Pool
    ): Generator.Result {
        // Build a read-only snapshot of the spatial plan for worker threads
        val snapshot = buildMap(spatialBin.size) {
            for ((k, v) in spatialBin) put(k, LinkedList(v))
        }
        stats.spatialBinCopies++
        stats.spatialBinAverageCopySize = (snapshot.size + stats.spatialBinCopies * stats.spatialBinAverageCopySize) / (stats.spatialBinCopies.toDouble() + 1)

        val candidates = computeCandidates(room, conn, pool, pool.rooms, snapshot)
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
                connections = candidateData.connections.associateWithTo(LinkedHashMap()) { null }
            )

            room.connections[conn] = candidate
            candidate.connections[candidateData.matchedConn] = room
            index(candidateData.chunks, candidate)
            stats.roomsPlanned++
            stats.descends++

            logDebug { "satisfyConnection(): descending into ${candidate.blueprint.id}." }

            val branchResult = when (val r = generate(candidate, depth + 1)) {
                is Generator.Result.Success -> generate(room, depth)
                else -> r
            }

            if (branchResult is Generator.Result.Success) {
                return Generator.Result.Success(room)
            }

            logDebug { "satisfyConnection(): back tracking ${candidate.blueprint.id} (${branchResult})." }
            stats.roomsPlanned--
            stats.ascends++
            unindex(candidateData.chunks, candidate)
            room.connections[conn] = null
            candidate.connections[candidateData.matchedConn] = null
            if (branchResult == Generator.Result.Failure.DEPTH_EXCEEDED){
                break
            }
        }

        logDebug { "satisfyConnection(): All candidates exhausted for this connection." }
        return Generator.Result.Failure.NO_VALID_CONNECTION
    }

    private fun pickNextConnector(room: PlannedRoom): JigsawConnection {
        val open = room.connections.keys.filter { room.connections[it] == null }
        return open.minBy { conn ->
            // smaller pool → more constrained → try first
            conn.pool?.rooms?.size ?: Int.MAX_VALUE
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
            stats.spatialBinAccesses++
            spatialBin.getOrPut(chunk, ::LinkedList).add(pr)
            stats.spatialBinMaxSize = max(stats.spatialBinMaxSize, spatialBin.size.toLong())
        }
    }

    private fun unindex(chunks: LongArray, pr: PlannedRoom) {
        for (chunk in chunks) {
            spatialBin[chunk]?.remove(pr)
        }
    }

    // Read-only intersection check that uses a snapshot instead of the live mutable map
    private fun isIntersectingSnapshot(
        chunks: LongArray,
        candidate: Region,
        snap: Map<Long, LinkedList<PlannedRoom>>
    ): Boolean {
        stats.chunksChecked += chunks.size
        for (c in chunks) {
            val possible = snap[c] ?: continue
            for (r in possible) {
                stats.intersectionsChecked++
                if (r.bounds.intersectsAabb(candidate)) {
                    stats.intersectionsFailed++
                    return true
                }
            }
        }
        return false
    }

    // Result container passed from worker to owner
    private class CandidateResult(
        val bp: RoomBlueprint,
        val rot: Rotation,
        val pos: BlockVec,
        val region: Region,
        val connections: List<JigsawConnection>,
        val matchedConn: JigsawConnection,
        val chunks: LongArray
    )

    // Owner spawns workers on Default dispatcher to evaluate candidates against snapshot
    private suspend fun computeCandidates(
        owner: PlannedRoom,
        hostConnection: JigsawConnection,
        pool: Pool,
        rooms: WeightedRandomList<String>,
        snapshot: Map<Long, LinkedList<PlannedRoom>>
    ): List<CandidateResult> = coroutineScope {
        val tasks = ArrayList<Deferred<CandidateResult?>>()
        val maxRooms = rooms.size
        val sampled = LinkedHashSet<String>(maxRooms)
        val inverted = hostConnection.direction.opposite()

        // Currently samples every room in the pool to evaluate at the same time
        while (sampled.size < maxRooms) {
            sampled += rooms.weightedRandom(random)
        }

        for (roomId in sampled) {
            val bp = roomset.rooms[roomId] ?: continue
            for (rot in Rotation.entries/*.shuffled(random)*/) {
                val candidateConnections = bp.connectionsWith(rot)
                var hasReciprocalConnection = false
                for (childConnection in candidateConnections) {
                    val childPool = childConnection.pool ?: break
                    if (pool.id !in childPool.connectedPools && childPool.id !in pool.connectedPools) {
                        LOGGER.debug("{} !in ({}) {} && {} !in ({}) {}", pool.id, childPool.id, childPool.connectedPools, childPool.id, pool.id, pool.connectedPools)
                        continue
                    }
                    hasReciprocalConnection = true
                    if (childConnection.direction != inverted) continue
                    tasks += async(Dispatchers.Default) {
                        stats.candidatesTried++
                        val candidatePos = computeCandidatePosition(owner, hostConnection, childConnection)
                        val candidateRegion = bp.regionAt(candidatePos, rot)
                        val candidateBounds = candidateRegion.expand(-0.1)
                        val candidateChunks = candidateBounds.containedChunks()

                        if (!isWithinHeight(candidateBounds)) {
                            stats.heightBoundsFails++
                            return@async null
                        }
                        if (isIntersectingSnapshot(candidateChunks, candidateBounds, snapshot)) return@async null

                        CandidateResult(
                            bp = bp,
                            rot = rot,
                            pos = candidatePos,
                            region = candidateRegion,
                            connections = candidateConnections,
                            matchedConn = childConnection,
                            chunks = candidateChunks
                        )
                    }
                }
                if (!hasReciprocalConnection) {
                    LOGGER.warn("No connections in room '$roomId' were in pool '${pool.id}', yet '$roomId' is defined in pool '${pool.id}'.")
                    break
                }
            }
        }

        if (tasks.isEmpty()) return@coroutineScope emptyList()
        tasks.awaitAll().filterNotNull()
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
