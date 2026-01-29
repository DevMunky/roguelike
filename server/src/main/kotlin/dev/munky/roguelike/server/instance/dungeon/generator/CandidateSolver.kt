package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.Pool
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.JigsawData
import dev.munky.roguelike.server.instance.dungeon.roomset.room.RoomBlueprint
import dev.munky.roguelike.server.interact.Region
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import kotlin.collections.plusAssign

/**
 * Computes valid generation candidates in parallel.
 */
open class CandidateSolver(
    minY: Int?,
    maxY: Int?,
) {
    var stats: Generator.Stats? = null
    val spatialRegion = SpatialRegion(minY, maxY)
    /**
     * Called just before generation starts.
     */
    open fun ensureReady(stats: Generator.Stats? = null) {
        this.stats = stats
        spatialRegion.ensureReady(stats)
    }

    fun computeCandidates(
        owner: PlannedRoom,
        hostConnection: ConnectionFeature,
        hostPool: Pool
    ): List<CandidateResult> {
        val tasks = ArrayList<CandidateResult?>()
        val inverted = hostConnection.direction.opposite()

        // Could probably eliminate some connections early based
        // on some statistic

        // For each room in the sampled blueprints
        for ((roomId, weight) in hostPool.entries.elements) {
            var hasAnyConnection = false
            val bp = owner.blueprint.roomSet.rooms[roomId] ?: continue

            // for each possible rotation
            for (rot in Rotation.entries/*.shuffled(random)*/) {
                val candidateConnections = bp.featuresWith(rot).connections
                // For each connection of the room in the current rotation
                for (candidateConnection in candidateConnections) {
                    // Skip if it is not facing the correct direction
                    if (candidateConnection.direction != inverted) continue
                    // Skip if there is an invalid pool on this connection
                    val candidatePool = candidateConnection.pool ?: continue
                    // Skip if not connected to our pool
                    if (!hostPool.isConnected(candidatePool)) {
                        LOGGER.warn(
                            "{} !in {} ({}) && {} !in {} ({})",
                            hostPool.id,
                            candidatePool.id,
                            candidatePool.connectedPools,
                            candidatePool.id,
                            hostPool.id,
                            hostPool.connectedPools
                        )
                        continue
                    }
                    hasAnyConnection = true
                    tasks += computeCandidate(owner, rot, bp, hostConnection, candidateConnection, weight)
                }
            }

            if (!hasAnyConnection) {
                error("No connections in room '$roomId' were in pool '${hostPool.id}', yet '$roomId' is defined in pool '${hostPool.id}'.")
            }
        }

        return tasks.filterNotNullTo(ArrayList(tasks.size))
    }

    protected fun computeCandidate(
        owner: PlannedRoom,
        rotation: Rotation,
        blueprint: RoomBlueprint<*>,
        hostConnection: ConnectionFeature,
        childConnection: ConnectionFeature,
        weight: Double,
    ) : CandidateResult? {
        stats?.candidatesTried++
        val pos = JigsawData.getAlignedPosition(owner.position, hostConnection, childConnection)
        val region = blueprint.boundsWith(pos, rotation)
        val slightlySmallerBounds = region.expand(-0.05)

        if (!spatialRegion.isInHeightBounds(slightlySmallerBounds)) {
            return null
        }

        if (spatialRegion.isIntersecting(slightlySmallerBounds)) {
            return null
        }

        return CandidateResult(
            blueprint = blueprint,
            rotation = rotation,
            position = pos,
            bounds = region,
            connectedToParentVia = childConnection,
            weight = weight
        )
    }

    // Result container passed from worker to owner
    class CandidateResult(
        val blueprint: RoomBlueprint<*>,
        val position: BlockVec,
        val rotation: Rotation,
        val bounds: Region,

        val weight: Double,
        val connectedToParentVia: ConnectionFeature
    ) {
        fun toPlannedRoom() = PlannedRoom(
            blueprint = blueprint,
            position = position,
            rotation = rotation,
            bounds = bounds,
            connectedToParentVia = connectedToParentVia
        )
    }

    companion object {
        private val LOGGER = logger {}
    }
}