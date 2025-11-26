package dev.munky.roguelike.server.instance.town

import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.instance.RoguelikeInstance
import dev.munky.roguelike.server.instance.dungeon.ElevatorRenderer
import dev.munky.roguelike.server.instance.mainmenu.MainMenuInstance
import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import org.joml.Vector3d
import java.util.*
import kotlin.time.Duration.Companion.seconds

class TownInstance private constructor() : RoguelikeInstance(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        chunkLoader = AnvilLoader("town")
        setGenerator {
            it.modifier().fillHeight(-64, 0, Block.STONE)
        }

        val origin = Vector3d(0.0, 0.0, 0.0)

        createArea {
            cuboid(
                Vector3d(1.0).mul(2.0).add(origin),
                Vector3d(1.0).mul(-2.0).add(origin)
            )
            bufferTime(3.seconds)
            thickness(.5)
            onExit {
                it.sendMessage("Exited cuboid")
            }
            onEnter {
                it.sendMessage("Entered cuboid")
            }
        }
        createArea {
            sphere(
                Vector3d(-5.0, .0, -5.0).add(origin),
                4.0
            )
            bufferTime(3.seconds)
            thickness(.5)
            onExit {
                it.sendMessage("Exited sphere")
            }
            onEnter {
                it.sendMessage("Entered sphere")
//                RenderDispatch.with(ElevatorRenderer)
//                    .with(it)
//                    .with(MainMenuInstance.create())
//                    .dispatch()
            }
        }
    }

    override fun onEnter(player: RoguelikePlayer) {
        RenderDispatch.with(TownRenderer)
            .with(player)
            .dispatch()
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