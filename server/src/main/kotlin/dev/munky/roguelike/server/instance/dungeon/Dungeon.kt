package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.server.instance.town.TownInstance.Companion.TOWN_DIMENSION_KEY
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import java.util.*

class Dungeon private constructor(
    val roomset: RoomSet
) : InstanceContainer(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        setGenerator {
            it.modifier().fillHeight(-64, -45, Block.STONE)
        }
    }

    companion object {
        // builder?
    }
}