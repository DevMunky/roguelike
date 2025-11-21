package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.SerialVector3i
import kotlinx.serialization.Serializable
import net.minestom.server.collision.BoundingBox
import net.minestom.server.collision.Shape
import net.minestom.server.coordinate.Vec
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType

@Serializable
data class RoomSet(
    val name: String,
    val dimension: String,
    val rooms: Map<String, Room>
) {

}

@Serializable
data class Room(
    val name: String,
    val entrances: List<SerialVector3i>,
    val max: SerialVector3i,
) : Shape by BoundingBox(Vec(0.0), Vec(max.x().toDouble(), max.y().toDouble(), max.z().toDouble())) {

}