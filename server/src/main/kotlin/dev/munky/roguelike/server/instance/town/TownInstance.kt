package dev.munky.roguelike.server.instance.town

import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.ModifierSelectRenderer
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import org.joml.Vector3d
import java.util.*
import kotlin.math.PI

class TownInstance private constructor() : RogueInstance(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        chunkLoader = AnvilLoader("town")
        setGenerator {
            it.modifier().fillHeight(-64, 0, Block.STONE)
        }

        val origin = Vector3d(-10.0, 0.0, 0.0)

        createArea {
            cuboid(
                Vector3d(1.5).mul(2.0).add(origin),
                Vector3d(1.5).mul(-2.0).add(origin)
            )
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

    override fun onEnter(player: RoguePlayer) {
        RenderDispatch.with(TownRenderer)
            .with(this)
            .with(player)
            .dispatchManaged()
        val mods = Roguelike.server().modifiers().toList()
        RenderDispatch.with(ModifierSelectRenderer)
            .with(player)
            .with(this)
            .with(ModifierSelectRenderer.Width, PI / 2.0)
            .with(ModifierSelectRenderer.Radius, 3.0)
            .with(ModifierSelectRenderer.ModifierSelection, listOf(mods[0], mods[1], mods[2]))
            .with(RenderKey.Position, Pos(0.0, 0.0, 0.0, 45f, 0f))
            .dispatchManaged()
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