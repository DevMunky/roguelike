package dev.munky.roguelike.server.instance.mainmenu

import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.interact.Conversation
import dev.munky.roguelike.server.interact.NpcPlayer
import dev.munky.roguelike.server.interact.conversation
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import java.util.*

/**
 * Per-player instance where players can select a character or create a new one, then transfer to town.
 */
class MainMenuInstance private constructor() : RogueInstance(UUID.randomUUID(), MENU_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        setGenerator {
            it.modifier().fillHeight(-64, 0, Block.BLACK_CONCRETE)
        }
    }

    override fun onEnter(player: RoguePlayer) {
        RenderDispatch.with(MainMenuRenderer)
            .with(player)
            .dispatchManaged()
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