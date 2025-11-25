package dev.munky.roguelike.server.instance.town

import dev.munky.roguelike.server.instance.RoguelikeInstance
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
            it.modifier().fillHeight(-64, -45, Block.STONE)
        }

        val origin = Vector3d(0.0, -45.0, 0.0)

        createArea {
            cuboid(origin.sub(Vector3d(1.0).mul(2.0)), Vector3d(1.0).mul(2.0).apply {
                y -= 45
            })
            bufferTime(3.seconds)
            thickness(2.0)
            onExit {
                it.sendMessage("Exited")
            }
            onEnter {
                it.sendMessage("Entered")
            }
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