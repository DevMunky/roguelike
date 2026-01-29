package dev.munky.roguelike.server.instance.dungeon.roomset

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.instance.dungeon.roomset.room.RoomData
import kotlinx.serialization.Serializable
import kotlin.collections.iterator

@Serializable
data class RoomSetData(
    val id: String,
    val dimensionKey: String,
    val rooms: Map<String, RoomData>,
    /**
     * A map of pool names to groupings of rooms.
     */
    val roomPools: Map<String, PoolData>,
    val enemyPools: Map<String, PoolData>,
) {
    init {
        for ((id, element) in roomPools) when (element) {
            is ReferencePoolData -> error("Must not have a reference pool in object 'room_pools', nothing to reference.")
            is UnionPoolData -> {
                for (pool in element.pools) if (pool is ReferencePoolData && !roomPools.containsKey(pool.id)) {
                    LOGGER.warn("Pool '$id' of roomset '${this.id}' references pool '$pool' which does not exist.")
                }
            }
            is TerminalPoolData -> {
                for (room in element.rooms.keys) if (!rooms.containsKey(room)) {
                    LOGGER.warn("Pool '$id' of roomset '${this.id}' references room '$room' which does not exist.")
                }
            }
        }
    }

    companion object {
        val LOGGER = logger {}
    }
}