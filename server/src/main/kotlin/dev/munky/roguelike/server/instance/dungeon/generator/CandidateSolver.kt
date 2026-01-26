package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.instance.dungeon.roomset.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.Pool
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import dev.munky.roguelike.server.interact.Region
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import kotlin.collections.plusAssign

/**
 * Computes valid generation candidates in parallel.
 */
open class CandidateSolver(
    val spatialRegion: SpatialRegion,
    val stats: Generator.Stats? = null,
) {
    /**
     * Called just before generation starts.
     */
    open fun ensureReady() {
        spatialRegion.ensureReady()
    }

    suspend fun computeCandidates(
        owner: PlannedRoom,
        hostConnection: ConnectionFeature,
        hostPool: Pool,
        sampled: LinkedHashSet<String>
    ): List<CandidateResult> =
        computeCandidates(owner, hostConnection, hostPool, sampled, spatialRegion.createSnapshot().also {
            stats?.spatialBinCopies++
            stats?.spatialBinAverageCopySize = (it.size + stats.spatialBinCopies * stats.spatialBinAverageCopySize) / (stats.spatialBinCopies.toDouble() + 1)
        })

    // Owner spawns workers on Default dispatcher to evaluate candidates against snapshot
    suspend fun computeCandidates(
        owner: PlannedRoom,
        hostConnection: ConnectionFeature,
        hostPool: Pool,
        sampled: LinkedHashSet<String>,
        snapshot: SpatialRegion.Snapshot
    ): List<CandidateResult> = coroutineScope {
        val tasks = ArrayList<Deferred<CandidateResult?>>()
        val inverted = hostConnection.direction.opposite()

        for (roomId in sampled) {
            val bp = owner.blueprint.roomset.rooms[roomId] ?: continue
            for (rot in Rotation.entries/*.shuffled(random)*/) {
                val candidateConnections = bp.featuresWith(rot).connections
                var hasReciprocalConnection = false
                for (childConnection in candidateConnections) {
                    val childPool = childConnection.pool ?: break
                    if (!hostPool.isConnected(childPool)) {
                        LOGGER.debug(
                            "{} !in ({}) {} && {} !in ({}) {}",
                            hostPool.id,
                            childPool.id,
                            childPool.connectedPools,
                            childPool.id,
                            hostPool.id,
                            hostPool.connectedPools
                        )
                        continue
                    }
                    hasReciprocalConnection = true
                    if (childConnection.direction != inverted) continue
                    tasks += async {
                        computeCandidate(snapshot, owner, rot, bp, hostConnection, childConnection)
                    }
                }
                if (!hasReciprocalConnection) {
                    LOGGER.warn("No connections in room '$roomId' were in pool '${hostPool.id}', yet '$roomId' is defined in pool '${hostPool.id}'.")
                    break
                }
            }
        }

        if (tasks.isEmpty()) return@coroutineScope emptyList()
        tasks.awaitAll().filterNotNull()
    }

    protected fun computeCandidate(
        snapshot: SpatialRegion.Snapshot,
        owner: PlannedRoom,
        rotation: Rotation,
        blueprint: RoomBlueprint,
        hostConnection: ConnectionFeature,
        childConnection: ConnectionFeature,
    ) : CandidateResult? {
        stats?.candidatesTried++
        val pos = owner.computeCandidatePosition(hostConnection, childConnection)
        val region = blueprint.boundsAt(pos, rotation)
        val slightlySmallerBounds = region.expand(-0.1)
        val chunks = slightlySmallerBounds.containedChunks()

        if (!spatialRegion.isInHeightBounds(slightlySmallerBounds)) {
            stats?.heightBoundsFails++
            return null
        }

        stats?.intersectionsChecked++
        stats?.chunksChecked += chunks.size
        if (spatialRegion.isIntersecting(chunks, slightlySmallerBounds, snapshot)) {
            stats?.intersections++
            return null
        }

        return CandidateResult(
            blueprint = blueprint,
            rotation = rotation,
            position = pos,
            bounds = region,
            chunks = chunks,
            matchedConnection = childConnection,
        )
    }

    // Result container passed from worker to owner
    class CandidateResult(
        val blueprint: RoomBlueprint,
        val position: BlockVec,
        val rotation: Rotation,
        val bounds: Region = blueprint.boundsAt(position, rotation),

        val matchedConnection: ConnectionFeature,
        val chunks: LongArray
    ) {
        fun toPlannedRoom() = PlannedRoom(
            blueprint = blueprint,
            position = position,
            rotation = rotation,
            bounds = bounds,
            matchedConnection = matchedConnection
        )
    }

    companion object {
        private val LOGGER = logger {}
    }
}