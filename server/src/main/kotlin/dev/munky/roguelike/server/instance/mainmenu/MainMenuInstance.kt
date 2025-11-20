package dev.munky.roguelike.server.instance.mainmenu

import dev.munky.roguelike.server.Roguelike
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.utils.chunk.ChunkSupplier
import net.minestom.server.world.DimensionType
import java.util.*

/**
 * Per-player instance where players can select a character or create a new one, then transfer to town.
 */
class MainMenuInstance private constructor() : InstanceContainer(UUID.randomUUID(), HUB_DIMENSION_KEY) {
    init {
        chunkSupplier = ::LightingChunk as ChunkSupplier
        setGenerator {
            it.modifier().fillHeight(-64, 0, Block.GRASS_BLOCK)
        }
    }

    companion object {
        val HUB_DIMENSION: DimensionType = DimensionType.builder()
            .coordinateScale(1.0)
            .fixedTime(0)
            .ambientLight(2f)
            .hasSkylight(false)
            .natural(true)
            .build()
        val HUB_DIMENSION_KEY: RegistryKey<DimensionType> = MinecraftServer.getDimensionTypeRegistry()
            .register("${Roguelike.NAMESPACE}:main_menu", HUB_DIMENSION)

        fun create() : MainMenuInstance {
            val hub = MainMenuInstance()
            MinecraftServer.getInstanceManager().registerInstance(hub)
            return hub
        }
    }
}