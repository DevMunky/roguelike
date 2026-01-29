package dev.munky.roguelike.server.instance.dungeon.roomset.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface RoomData

@Serializable
@SerialName("normal")
class NormalRoomData() : RoomData

@Serializable
@SerialName("multipart")
data class MultipartRoomData(
    /**
     * 2D matrix of structure ids.
     */
    val structures: List<List<String>>
) : RoomData