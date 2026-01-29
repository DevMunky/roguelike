package dev.munky.roguelike.server.instance.dungeon.roomset

import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.EnemyFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.RoomJoinFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.room.MultipartRoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.room.MultipartRoomData
import dev.munky.roguelike.server.instance.dungeon.roomset.room.NormalRoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.room.NormalRoomData
import dev.munky.roguelike.server.instance.dungeon.roomset.room.RoomBlueprint
import kotlinx.coroutines.*
import java.util.*

class RoomSet private constructor(val data: RoomSetData) {
    val id = data.id

    /**
     * The pools defined within this roomset.
     */
    val pools = data.roomPools.mapValues { (k, v) -> Pool.of(this, k, v) }

    val rootRoomId = "$id/$ROOT_ROOM_NAME"

    /**
     * Must not be accessed before [initialized][initialize].
     */
    var rooms: Map<String, RoomBlueprint<*>> = emptyMap()
        private set

    private suspend fun initialize() {
        rooms = createRooms()
    }

    private suspend fun createRooms() : Map<String, RoomBlueprint<*>> = coroutineScope {
        var hasTerminal = false
        val jobs = arrayListOf<Job>()
        val map = LinkedHashMap<String, RoomBlueprint<*>>().apply {
            for (room in data.rooms) {
                val room = when (val data = room.value) {
                    is NormalRoomData -> NormalRoomBlueprint(room.key, this@RoomSet, data)
                    is MultipartRoomData -> MultipartRoomBlueprint(room.key, this@RoomSet, data)
                }

                set(room.id, room)
                jobs += launch { room.initialize() }
            }
        }
        jobs.joinAll()
        for (room in map.values) hasTerminal = hasTerminal || room.isTerminal
        if (!hasTerminal) error("Roomset must have at least one terminal room (a room with only one connection).")
        map
    }

    companion object {
        const val ROOT_ROOM_NAME = "root"

        suspend fun create(roomSetData: RoomSetData) : RoomSet = RoomSet(roomSetData).apply {
            initialize()
        }
    }
}

/**
 * The features of a room.
 *
 * @see RoomBlueprint.featuresWith
 * @see ConnectionFeature
 * @see EnemyFeature
 */
class RoomFeatures(
    val connections: Array<ConnectionFeature>,
    val enemies: Array<EnemyFeature>,
    val joins: Array<RoomJoinFeature>
) {
    companion object {
        val EMPTY = RoomFeatures(emptyArray(), emptyArray(), emptyArray())
    }
}