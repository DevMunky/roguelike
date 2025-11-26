package dev.munky.roguelike.server.instance.dungeon.example

import dev.munky.roguelike.server.instance.dungeon.Room
import java.util.UUID
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import dev.munky.roguelike.server.instance.dungeon.RoomSet
import net.minestom.server.world.DimensionType
import net.minestom.server.registry.RegistryKey
import org.joml.Vector3i
import net.minestom.server.instance.batch.Batch

//Creates a set of bounding boxes "Rooms" from mca and loads as a new instance
class ExampleRoomSet private constructor(): InstanceContainer(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        chunkLoader = AnvilLoader("server/run/exampleworld")
    }
    companion object {
        val TOWN_DIMENSION: DimensionType = DimensionType.builder()
            .coordinateScale(1.0)
            .fixedTime(0)
            .ambientLight(2f)
            .hasSkylight(false)
            .natural(true)
            .effects("the_end")
            .build()
        val TOWN_DIMENSION_KEY: RegistryKey<DimensionType> by lazy { MinecraftServer.getDimensionTypeRegistry().getKey(TOWN_DIMENSION)!! }

        //Entrances: a list of 3d vectors
        //"max" a 3d vector
        val roomone = Room("room_one",
            listOf( Vector3i(0,0,0), Vector3i(4,0,4) ),
            Vector3i(4,1,4))

        val setData = RoomSet(
            "ExampleRoomSet",
            "TOWN_DIMENSION",
            mapOf(roomone.name to roomone)
        )

        fun create(): ExampleRoomSet {
            val exampleroomset = ExampleRoomSet()
            MinecraftServer.getInstanceManager().registerInstance(exampleroomset)
            return exampleroomset
        }
    }
}