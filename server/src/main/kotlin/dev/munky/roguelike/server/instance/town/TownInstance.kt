package dev.munky.roguelike.server.instance.town

import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import java.util.UUID

class TownInstance private constructor() : InstanceContainer(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        chunkLoader = AnvilLoader("town")
        setGenerator {
            it.modifier().fillHeight(-64, -45, Block.STONE)
        }
    }

    companion object {
        val TOWN_DIMENSION: DimensionType = DimensionType.builder()
            .coordinateScale(1.0)
            .fixedTime(0)
            .ambientLight(1f)
            .hasSkylight(false)
            .natural(true)
            .effects("the_end")
            .build()
        val TOWN_DIMENSION_KEY: RegistryKey<DimensionType> by lazy { MinecraftServer.getDimensionTypeRegistry().getKey(TOWN_DIMENSION)!! }

        fun create() : TownInstance {
            val town = TownInstance()
            MinecraftServer.getInstanceManager().registerInstance(town)
            return town
        }
    }
}