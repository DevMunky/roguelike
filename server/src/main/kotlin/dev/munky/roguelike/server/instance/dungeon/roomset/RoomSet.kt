package dev.munky.roguelike.server.instance.dungeon.roomset

import dev.munky.roguelike.common.WeightedRandomList
import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.loadChunksInGlobal
import dev.munky.roguelike.server.rotate
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import net.hollowcube.schem.BlockEntityData
import net.hollowcube.schem.Structure
import net.hollowcube.schem.util.Rotation
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction
import java.util.EnumMap
import java.util.LinkedHashMap

class RoomSet private constructor(val data: RoomSetData) {
    val id = data.id

    val pools = data.pools.mapValues { (_, v) -> WeightedRandomList(v) }

    val rootRoomId = "$id/$ROOT_ROOM_ID"

    var rooms: Map<String, RoomBlueprint> = emptyMap()
        private set

    private suspend fun initialize() {
        rooms = createRooms()
    }

    private suspend fun createRooms() : Map<String, RoomBlueprint> = coroutineScope {
        val rooms = LinkedHashMap<String, RoomBlueprint>()
        for (room in data.rooms) {
            val room = RoomBlueprint(room.key, this@RoomSet, room.value)
            rooms[room.id] = room
            launch { room.initialize() }
        }
        rooms
    }

    companion object {
        const val ROOT_ROOM_ID = "root"

        suspend fun create(roomSetData: RoomSetData) : RoomSet = RoomSet(roomSetData).apply {
            initialize()
        }
    }
}

@Suppress("UnstableApiUsage")
data class RoomBlueprint(
    val id: String,
    val parent: RoomSet,
    val data: RoomData
) {
    private var structure: Structure = Structure(Vec.ZERO, emptyList(), emptyList(), emptyList())
    private val connectionCache = EnumMap<Rotation, List<JigsawConnection>>(Rotation::class.java)
    private val regionCache = EnumMap<Rotation, Region.Cuboid>(Rotation::class.java)

    suspend fun initialize() {
        try {
            structure = Roguelike.server().structures()[id] ?: error("No structure named '$id' found.")
        } catch (t: Throwable) {
            System.err.println("Failed to load structure for room '$id'.")
            t.printStackTrace()
            //throw RuntimeException("Failed to load structure for room '$id'.", t)
        }
    }

    fun connectionsWith(rotation: Rotation) : List<JigsawConnection> {
        connectionCache[rotation]?.let {
            return it
        }

        val result = ArrayList<JigsawConnection>()
        if (structure.blocks.isEmpty()) return result

        val palette = structure.palettes.firstOrNull() ?: return result

        for (bi in structure.blocks) {
            val block = palette[bi.paletteIndex]
            // Identify jigsaw blocks
            val isJigsaw = block.compare(Block.JIGSAW, Block.Comparator.ID)
            if (!isJigsaw) continue

            val nbt = bi.blockEntity?.data ?: continue

            // Rotate the local position into the rotated local space (origin at min corner),
            // then convert to an offset relative to the center of the rotated structure.
            val localRot = rotateAboutCenter(bi.pos, rotation)
            val offsetFromCenter = localToCenter(localRot, rotation)

            val name = nbt.getString("name")
            val pool = nbt.getString("pool")
            val target = nbt.getString("target")

            val finalBlockData = nbt.getString("final_state")
            val finalBlock = Block.fromState(finalBlockData) ?: error("Jigsaw block '$name' with target '$target' at schematic location ${bi.pos} has invalid block data $finalBlockData.")

            val orientation = block.getProperty("orientation")!! // minecraft must export
            val baseDir = directionFromOrientation(orientation)
            val dir = rotation.rotate(baseDir)

            result.add(
                JigsawConnection(
                    name = name,
                    pool = pool,
                    finalBlock = finalBlock,
                    target = target,
                    position = offsetFromCenter,
                    direction = dir
                )
            )
        }

        // Cache to avoid recomputing
        connectionCache[rotation] = result
        return result
    }

    fun regionAt(at: Point, rotation: Rotation) : Region.Cuboid {
        val r = regionCache.getOrPut(rotation) { computeRegion(rotation) }
        return r.offset(at.toJoml()) as Region.Cuboid
    }

    private fun computeRegion(rotation: Rotation) : Region.Cuboid {
        val min = centerToMin(Vec.ZERO, rotation)
        val max = min.add(rotatedSize(rotation)).asBlockVec()
        return Region.Cuboid(min.toJoml(), max.toJoml())
    }

    /**
     * Returns the area of this room post-rotation.
     */
    suspend fun paste(instance: Instance, at: Point, rotation: Rotation = Rotation.NONE) : Region.Cuboid {
        val area = regionAt(at, rotation)
        val min = area.min

        val chunks = area.containedChunks()
        val tasks = ArrayList<Deferred<*>>()
        for (chunk in chunks) tasks += instance.loadChunk(
            CoordConversion.chunkIndexGetX(chunk),
            CoordConversion.chunkIndexGetZ(chunk)
        ).asDeferred()
        tasks.joinAll()

        setBlocksUnsafe(instance, min.x(), min.y(), min.z(), rotation)
        return area
    }

    /**
     * Does not load chunks, ensure chunks are loaded before calling.
     */
    private suspend fun setBlocksUnsafe(instance: Instance, x: Double, y: Double, z: Double, rotation: Rotation) {
        val blockMgr = MinecraftServer.getBlockManager()
        val changedChunks = HashSet<Chunk>()
        withContext(Dispatchers.IO) {
            var chunk: Chunk
            val palette = structure.palettes.first()
            var block: Block
            var blockEntity: BlockEntityData?
            var first = true
            for (bi in structure.blocks) {
                block = palette[bi.paletteIndex]
                if (first) {
                    first = false
                    block = Block.DIAMOND_BLOCK
                }
                blockEntity = bi.blockEntity

                if (blockEntity != null) {
                    // Lower case the IDs always to prevent parse errors, especially for legacy names like 'Beacon'
                    val lowerKey = blockEntity.id.lowercase()
                    @Suppress("UnstableApiUsage")
                    block = block.withHandler(blockMgr.getHandlerOrDummy(lowerKey))
                        .withNbt(blockEntity.data)

                    if (block.compare(Block.JIGSAW, Block.Comparator.ID)) {
                        val final = block.nbtOrEmpty().getString("final_state")
                        block = Block.fromState(final) ?: error("Invalid final state $final for jigsaw block ${bi.pos}.")
                    }
                }
                // rotated local position, then translate by min
                val localRot = rotateAboutCenter(bi.pos, rotation)
                val pos: Point = localRot.add(x, y, z)
                block = block.rotate(rotation)

                val cx = CoordConversion.globalToChunk(pos.x())
                val cz = CoordConversion.globalToChunk(pos.z())
                chunk = instance.getChunk(cx, cz) ?: error("chunk $cx, $cz not loaded")
                synchronized(chunk) {
                    chunk.setBlock(pos, block)
                }
                changedChunks.add(chunk)
            }
        }
        changedChunks.forEach(Chunk::sendChunk)
    }

    /**
     * Rotate a local block position around the center of the structure's Y axis (integer grid preserving),
     * returning a BlockVec in the rotated local space (origin at min corner of rotated AABB).
     */
    private fun rotateAboutCenter(pos: Point, rotation: Rotation): BlockVec {
        val x = pos.blockX()
        val y = pos.blockY()
        val z = pos.blockZ()
        val sx = structure.size.blockX()
        val sz = structure.size.blockZ()

        return when (rotation) {
            Rotation.NONE -> pos.asBlockVec()
            Rotation.CLOCKWISE_90 -> BlockVec(sz - 1 - z, y, x)
            Rotation.CLOCKWISE_180 -> BlockVec(sx - 1 - x, y, sz - 1 - z)
            Rotation.CLOCKWISE_270 -> BlockVec(z, y, sx - 1 - x)
        }
    }

    /**
     * Size of the structure after applying rotation (x/z swapped for 90/270).
     */
    private fun rotatedSize(rotation: Rotation): Point = when (rotation) {
        Rotation.NONE, Rotation.CLOCKWISE_180 -> structure.size
        Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_270 -> Vec(structure.size.z(), structure.size.y(), structure.size.x())
    }

    /**
     * Convert a rotated local-space position (min-corner origin) into an offset from the center
     * of the rotated structure. Returned as BlockVec for consistency with existing API.
     */
    private fun localToCenter(localRot: BlockVec, rotation: Rotation): BlockVec {
        val half = rotatedSize(rotation).div(2.0)
        return localRot.sub(half).asBlockVec()
    }

    /**
     * Compute min-corner position from a center position for this structure with rotation.
     */
    private fun centerToMin(atCenter: Point, rotation: Rotation): Point {
        val half = rotatedSize(rotation).div(2.0)
        return atCenter.sub(half)
    }

    private fun directionFromOrientation(orientation: String) : Direction {
        return when (orientation) {
            "north_up" -> Direction.NORTH
            "east_up" -> Direction.EAST
            "south_up" -> Direction.SOUTH
            "west_up" -> Direction.WEST
            else -> if (orientation.startsWith("up")) Direction.UP else Direction.DOWN
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoomBlueprint

        if (id != other.id) return false
        if (parent != other.parent) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + parent.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}

data class JigsawConnection(
    val name: String,
    val pool: String,
    val finalBlock: Block,
    val target: String,

    val position: BlockVec,
    val direction: Direction
)

@Serializable
data class RoomSetData(
    val id: String,
    val dimensionKey: String,
    val rooms: Map<String, RoomData>,
    /**
     * A map of pool names to groupings of rooms.
     */
    val pools: Map<String, Map<String, Double>>
) {
    init {
        for ((id, element) in pools) {
            for (room in element.keys) if (!rooms.containsKey(room)) {
                LOGGER.warn("Pool '$id' of Roomset '${this.id}' references room '$room' which does not exist.")
            }
        }
    }

    companion object {
        val LOGGER = logger {}
    }
}

@Serializable
class RoomData()