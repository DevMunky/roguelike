package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.town.TownInstance.Companion.TOWN_DIMENSION_KEY
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.LightingChunk
import java.util.*

class Dungeon private constructor(
    val roomset: RoomSet
) : RogueInstance(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
    }

    private suspend fun initialize() {
        // place root room at origin
        val root = roomset.rooms[RoomSet.ROOT_ROOM_ID] ?: error("No root room '${RoomSet.ROOT_ROOM_ID}' defined.")
        root.paste(this@Dungeon, Vec(0.0, 0.0, 0.0))
    }

    companion object {
        suspend fun create(roomset: RoomSet) : Dungeon = Dungeon(roomset).apply {
            initialize()
            MinecraftServer.getInstanceManager().registerInstance(this)
        }
    }
}