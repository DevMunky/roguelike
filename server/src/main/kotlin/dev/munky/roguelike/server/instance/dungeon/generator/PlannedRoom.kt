package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.JigsawData
import dev.munky.roguelike.server.instance.dungeon.roomset.room.RoomBlueprint
import dev.munky.roguelike.server.interact.Region
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec

data class PlannedRoom(
    val blueprint: RoomBlueprint<*>,
    val position: BlockVec,
    val rotation: Rotation,
    val bounds: Region = blueprint.boundsWith(position, rotation),
    val connectedToParentVia: ConnectionFeature? = null
) {
    val connections: Connections = Connections()

    /**
     * Used to unindex the correct connection on the parent when this room is removed from the tree.
     *
     * @see GenerationOrchestrator.revert
     */
    var parentConnectsVia: ConnectionFeature? = null

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
        return "PlannedRoom(bounds=$bounds, rotation=$rotation, position=$position, blueprint=${blueprint.id}, connections=$connections"
    }

    inner class Connections {
        val allConnections = blueprint.featuresWith(rotation).connections
        // Only connections that are actually connectable (non-null pool with entries)
        private fun isConnectable(connection: ConnectionFeature): Boolean =
            connection.pool?.entries?.isNotEmpty() == true

        private var _openConnections = HashSet<ConnectionFeature>(allConnections.filter(::isConnectable))
        val openConnections: Set<ConnectionFeature> get() = _openConnections

        fun setOpen(connection: ConnectionFeature) {
            assertWithinRoom(connection)
            // Only track as open if it is a connectable connection
            if (isConnectable(connection)) {
                _openConnections += connection
            }
        }

        fun setClosed(connection: ConnectionFeature) {
            assertWithinRoom(connection)
            _openConnections -= connection
        }

        private fun assertWithinRoom(connection: ConnectionFeature) {
            if (connection !in allConnections) error("Connection '$connection' not part of this room.")
        }

        override fun toString(): String {
            return "Connections(openConnections=$openConnections)"
        }
    }
}