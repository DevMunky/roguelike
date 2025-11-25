package dev.munky.roguelike.server.instance.mainmenu

import dev.munky.roguelike.server.instance.RoguelikeInstance
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.SharedInstance
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import java.util.*

/**
 * Per-player instance where players can select a character or create a new one, then transfer to town.
 */
class MainMenuInstance private constructor() : RoguelikeInstance(UUID.randomUUID(), MENU_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        setGenerator {
            it.modifier().fillHeight(-64, -45, Block.BLACK_CONCRETE)
        }
    }

    companion object {
        val MENU_DIMENSION: DimensionType = DimensionType.builder()
            .coordinateScale(1.0)
            .fixedTime(0)
            .ambientLight(1f)
            .hasSkylight(false)
            .natural(true)
            .effects("the_end")
            .build()
        val MENU_DIMENSION_KEY: RegistryKey<DimensionType> by lazy { MinecraftServer.getDimensionTypeRegistry().getKey(MENU_DIMENSION)!! }

        fun create() : MainMenuInstance {
            val hub = MainMenuInstance()
            MinecraftServer.getInstanceManager().registerInstance(hub)
            return hub
        }
    }
}