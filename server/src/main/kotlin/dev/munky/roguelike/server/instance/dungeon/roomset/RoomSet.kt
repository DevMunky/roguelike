package dev.munky.roguelike.server.instance.dungeon.roomset

import dev.munky.modelrenderer.skeleton.UUID
import dev.munky.roguelike.common.SerialVector3i
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.loadChunksInChunks
import dev.munky.roguelike.server.loadChunksInGlobal
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.batch.BatchOption
import net.minestom.server.instance.batch.RelativeBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction

class RoomSet private constructor(val data: RoomSetData) : RogueInstance(
    UUID.randomUUID(),
    MinecraftServer.getDimensionTypeRegistry().getKey(Key.key(data.dimensionKey))!!
) {
    val id = data.id

    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
        chunkLoader = AnvilLoader("roomsets/anvil/$id")
        setGenerator {
            it.modifier().fillHeight(-64, 256, Block.GLASS)
        }
    }

    var rooms: Map<String, Room> = emptyMap()
        private set

    private suspend fun initialize() {
        rooms = createRooms()
    }

    private suspend fun createRooms() : Map<String, Room> = coroutineScope {
        val rooms = HashMap<String, Room>()
        for (room in data.rooms) {
            val room = Room(room.key, this@RoomSet, room.value)
            rooms[room.id] = room
            launch { room.initialize() }
        }
        rooms
    }

    data class Room(
        val id: String,
        val parent: RoomSet,
        val data: RoomData
    ) {
        val batchOptions: BatchOption = BatchOption().setCalculateInverse(false).setFullChunk(false)

        private var batch: RelativeBlockBatch = RelativeBlockBatch()

        suspend fun initialize() {
            batch = createBatch()
        }

        suspend fun paste(instance: Instance, at: Point) {
            instance.loadChunksInGlobal(data.lower.x()..data.upper.x(), data.lower.z()..data.upper.z())
            for (x in data.lower.x()..data.upper.x())
                for (y in data.lower.y()..data.upper.y())
                    for (z in data.lower.z()..data.upper.z()) {
                        val x = at.blockX() + x
                        val y = at.blockY() + y
                        val z = at.blockZ() + z
                        val block = when (val e = data.entrances.firstOrNull { it.position.equals(x, y, z) }) {
                            is EntranceData -> Block.JIGSAW.withProperty("orientation", "${e.direction.name.lowercase()}_up")
                            else -> parent.getBlock(x, y, z)
                        }
                        instance.setBlock(x, y, z, block)
                    }
//            batch.applyUnsafe(instance, at.x().toInt(), at.y().toInt(), at.z().toInt()) {
//                future.complete()
//            }
        }

        private suspend fun createBatch() : RelativeBlockBatch {
            val batch = RelativeBlockBatch(batchOptions)

            val minX = data.lower.x()
            val maxX = data.upper.x()
            val minY = data.lower.y()
            val maxY = data.upper.y()
            val minZ = data.lower.z()
            val maxZ = data.upper.z()

            parent.loadChunksInGlobal(minX..maxX, minZ..maxZ)

            for (x in minX..maxX)
                for (y in minY..maxY)
                    for (z in minZ..maxZ)
                        batch.setBlock(x, y, z, parent.getBlock(x, y, z))

            return batch
        }
    }

    companion object {
        const val ROOT_ROOM_ID = "root"

        suspend fun create(roomSetData: RoomSetData) : RoomSet = RoomSet(roomSetData).apply {
            initialize()
            MinecraftServer.getInstanceManager().registerInstance(this)
        }
    }
}

@Serializable
data class RoomSetData(
    val id: String,
    val dimensionKey: String,
    val rooms: Map<String, RoomData>
)

@Serializable
data class RoomData(
    val entrances: List<EntranceData>,
    val lower: SerialVector3i,
    val upper: SerialVector3i
)

@Serializable
data class EntranceData(
    /**
     * Relative to [RoomData.lower]
     */
    val position: SerialVector3i,
    val direction: Direction
)