package dev.munky.roguelike.server.instance.dungeon.roomset

import dev.munky.roguelike.common.WeightedRandomList
import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.rotate
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.hollowcube.schem.BlockEntityData
import net.hollowcube.schem.Structure
import net.hollowcube.schem.util.Rotation
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction
import org.jetbrains.annotations.Contract
import java.util.*

class RoomSet private constructor(val data: RoomSetData) {
    val id = data.id

    /**
     * Pool id -> WeightedRandomList of room ids.
     */
    val pools = data.roomPools.mapValues { (k, v) -> Pool.of(this, k, v) }

    val rootRoomId = "$id/$ROOT_ROOM_ID"

    var rooms: Map<String, RoomBlueprint> = emptyMap()
        private set

    private suspend fun initialize() {
        rooms = createRooms()
    }

    private suspend fun createRooms() : Map<String, NormalRoomBlueprint> = coroutineScope {
        LinkedHashMap<String, NormalRoomBlueprint>().apply {
            for (room in data.rooms) {
                val room = NormalRoomBlueprint(room.key, this@RoomSet, room.value)
                set(room.id, room)
                launch { room.initialize() }
            }
        }
    }

    companion object {
        const val ROOT_ROOM_ID = "root"

        suspend fun create(roomSetData: RoomSetData) : RoomSet = RoomSet(roomSetData).apply {
            initialize()
        }
    }
}

data class Pool (
    val id: String,
    val entries: WeightedRandomList<String>,
    val connectedPools: List<String>
) {
    fun isConnected(other: Pool) = connectedPools.contains(other.id) || other.connectedPools.contains(id)

    companion object {
        fun of(roomSet: RoomSet, id: String, data: PoolData) : Pool {
            val connections = arrayListOf(id)
            val rooms = HashMap<String, Double>()
            gatherRooms(roomSet.data.roomPools, rooms, data)
            gatherConnections(roomSet.data.roomPools, connections, data)
            return Pool(
                id,
                WeightedRandomList(rooms),
                connections
            )
        }

        private fun gatherConnections(pools: Map<String, PoolData>, connections: ArrayList<String>, pool: PoolData) {
            when (pool) {
                is ReferencePoolData -> gatherConnections(pools, connections, pools[pool.id]!!)
                is UnionPoolData -> pool.pools.forEach { gatherConnections(pools, connections, it) }

                is TerminalPoolData -> connections += pools.entries.first { it.value == pool }.key
            }
        }

        private fun gatherRooms(pools: Map<String, PoolData>, rooms: HashMap<String, Double>, pool: PoolData) {
            when (pool) {
                is ReferencePoolData -> gatherRooms(pools, rooms, pools[pool.id]!!)
                is UnionPoolData -> pool.pools.forEach { gatherRooms(pools, rooms, it) }

                is TerminalPoolData -> rooms.putAll(pool.rooms)
            }
        }
    }
}

data class RoomFeatures(
    val connections: Set<ConnectionFeature>,
    val enemies: Set<EnemyFeature>
) {
    companion object {
        val EMPTY = RoomFeatures(emptySet(), emptySet())
    }
}

sealed class RoomBlueprint(
    val id: String,
    val roomset: RoomSet,
    val data: RoomData,
) {
    protected val featureCache = EnumMap<Rotation, RoomFeatures>(Rotation::class.java)
    protected val regionCache = EnumMap<Rotation, Region.Cuboid>(Rotation::class.java)

    protected abstract val size: Point
    protected abstract suspend fun setBlocksUnsafe(instance: Instance, x: Double, y: Double, z: Double, rotation: Rotation)
    protected abstract fun computeFeaturesWith(rotation: Rotation) : RoomFeatures

    abstract suspend fun initialize()

    fun boundsAt(at: Point, rotation: Rotation) : Region.Cuboid {
        val r = regionCache.getOrPut(rotation) { computeRegion(rotation) }
        return r.offset(at.toJoml()) as Region.Cuboid
    }

    fun featuresWith(rotation: Rotation) : RoomFeatures {
        featureCache[rotation]?.let {
            return it
        }

        val result = computeFeaturesWith(rotation)

        // Cache to avoid recomputing
        featureCache[rotation] = result
        return result
    }

    /**
     * Returns the area of this room post-rotation.
     */
    suspend fun paste(instance: Instance, at: Point, rotation: Rotation = Rotation.NONE) : Region.Cuboid {
        val area = boundsAt(at, rotation)
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

    @Contract("_, _, null, _ -> null; _, _, !null, _ -> !null")
    protected fun featureFromBlock(block: Block, position: Point, nbt: CompoundBinaryTag?, rotation: Rotation) : JigsawData? {
        nbt ?: return null
        if (!block.compare(Block.JIGSAW, Block.Comparator.ID)) return null

        // Rotate the local position into the rotated local space (origin at min corner),
        // then convert to an offset relative to the center of the rotated structure.
        val localRot = rotateAboutCenter(position, rotation)
        val offsetFromCenter = localToCenter(localRot, rotation)

        val name = nbt.getString("name")
        val pool = nbt.getString("pool")
        val target = nbt.getString("target")

        val finalBlockData = nbt.getString("final_state")
        val finalBlock = Block.fromState(finalBlockData)
            ?: error("Jigsaw block '$name' with target '$target' at schematic location $position has invalid block data $finalBlockData.")

        val orientation = block.getProperty("orientation")!! // minecraft must export
        val baseDir = directionFromOrientation(orientation)
        val dir = rotation.rotate(baseDir)

        return when (target) {
            EnemyFeature.ID -> EnemyFeature(
                name = name,
                poolName = pool,
                pool = roomset.pools[pool],
                finalBlock = finalBlock,
                position = offsetFromCenter,
                direction = dir
            )
            else -> ConnectionFeature(
                name = name,
                poolName = pool,
                pool = roomset.pools[pool],
                finalBlock = finalBlock,
                position = offsetFromCenter,
                direction = dir
            )
        }
    }

    /**
     * Rotate a local block position around the center of the structure's Y axis (integer grid preserving),
     * returning a BlockVec in the rotated local space (origin at min corner of rotated AABB).
     */
    protected fun rotateAboutCenter(pos: Point, rotation: Rotation): BlockVec {
        val x = pos.blockX()
        val y = pos.blockY()
        val z = pos.blockZ()
        val sx = size.blockX()
        val sz = size.blockZ()

        return when (rotation) {
            Rotation.NONE -> pos.asBlockVec()
            Rotation.CLOCKWISE_90 -> BlockVec(sz - 1 - z, y, x)
            Rotation.CLOCKWISE_180 -> BlockVec(sx - 1 - x, y, sz - 1 - z)
            Rotation.CLOCKWISE_270 -> BlockVec(z, y, sx - 1 - x)
        }
    }

    protected fun computeRegion(rotation: Rotation) : Region.Cuboid {
        val half = rotatedSize(rotation).div(2.0)
        val min = Vec.ZERO.sub(half)
        val max = Vec.ZERO.add(half)
        return Region.Cuboid(min.toJoml(), max.toJoml())
    }
    /**
     * Size of the structure after applying rotation (x/z swapped for 90/270).
     */
    protected fun rotatedSize(rotation: Rotation): Point = when (rotation) {
        Rotation.NONE, Rotation.CLOCKWISE_180 -> size
        Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_270 -> Vec(size.z(), size.y(), size.x())
    }

    /**
     * Convert a rotated local-space position (min-corner origin) into an offset from the center
     * of the rotated structure. Returned as BlockVec for consistency with existing API.
     */
    protected fun localToCenter(localRot: BlockVec, rotation: Rotation): BlockVec {
        val half = rotatedSize(rotation).div(2.0)
        return localRot.sub(half).asBlockVec()
    }

    protected fun directionFromOrientation(orientation: String) : Direction {
        return when (orientation) {
            "north_up" -> Direction.NORTH
            "east_up" -> Direction.EAST
            "south_up" -> Direction.SOUTH
            "west_up" -> Direction.WEST
            else -> if (orientation.startsWith("up")) Direction.UP else Direction.DOWN
        }
    }
}

@Suppress("UnstableApiUsage")
private class NormalRoomBlueprint(
    id: String,
    parent: RoomSet,
    data: RoomData
) : RoomBlueprint(id, parent, data) {
    private var structure: Structure = Structure(Vec.ZERO, emptyList(), emptyList(), emptyList())
    override val size: Point get() = structure.size

    override suspend fun initialize() {
        try {
            structure = Roguelike.server().structures()[id] ?: error("No structure named '$id' found.")
        } catch (t: Throwable) {
            throw RuntimeException("Failed to load structure for room '$id'.", t)
        }
    }

    override fun computeFeaturesWith(rotation: Rotation): RoomFeatures {
        if (structure.blocks.isEmpty()) return RoomFeatures.EMPTY
        val palette = structure.palettes.firstOrNull() ?: return RoomFeatures.EMPTY

        val connections = HashSet<ConnectionFeature>()
        val enemies = HashSet<EnemyFeature>()

        for (bi in structure.blocks) {
            val block = palette[bi.paletteIndex]

            val nbt = bi.blockEntity?.data ?: continue

            val feature = featureFromBlock(block, bi.pos, nbt, rotation) ?: continue

            when (feature) {
                is ConnectionFeature -> connections.add(feature)
                is EnemyFeature -> enemies.add(feature)
            }
        }

        return RoomFeatures(connections, enemies)
    }

    /**
     * Does not load chunks, ensure chunks are loaded before calling.
     */
    override suspend fun setBlocksUnsafe(instance: Instance, x: Double, y: Double, z: Double, rotation: Rotation) {
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
}

sealed interface JigsawData {
    val name: String
    val poolName: String
    val finalBlock: Block
    /**
     * Used for identification of different features
     */
    val target: String

    val position: BlockVec
    val direction: Direction
}

data class EnemyFeature(
    override val name: String,
    override val poolName: String,
    val pool: Pool?,
    override val finalBlock: Block,

    override val position: BlockVec,
    override val direction: Direction
) : JigsawData {
    override val target: String get() = ID

    companion object {
        const val ID = "${Roguelike.NAMESPACE}:enemy"
    }
}

data class ConnectionFeature(
    override val name: String,
    override val poolName: String,
    val pool: Pool?,
    override val finalBlock: Block,

    override val position: BlockVec,
    override val direction: Direction
) : JigsawData {
    override val target: String get() = ID

    companion object {
        const val ID = "${Roguelike.NAMESPACE}:connection"
    }
}

@Serializable
data class RoomSetData(
    val id: String,
    val dimensionKey: String,
    val rooms: Map<String, RoomData>,
    /**
     * A map of pool names to groupings of rooms.
     */
    val roomPools: Map<String, PoolData>,
    val enemyPools: Map<String, PoolData>,
) {
    init {
        for ((id, element) in roomPools) when (element) {
            is ReferencePoolData -> error("Must not have a reference pool in object 'room_pools', nothing to reference.")
            is UnionPoolData -> {
                for (pool in element.pools) if (pool is ReferencePoolData && !roomPools.containsKey(pool.id)) {
                    LOGGER.warn("Pool '$id' of roomset '${this.id}' references pool '$pool' which does not exist.")
                }
            }
            is TerminalPoolData -> {
                for (room in element.rooms.keys) if (!rooms.containsKey(room)) {
                    LOGGER.warn("Pool '$id' of roomset '${this.id}' references room '$room' which does not exist.")
                }
            }
        }
    }

    companion object {
        val LOGGER = logger {}
    }
}

@Serializable
sealed interface PoolData

@Serializable
@SerialName("reference")
data class ReferencePoolData(
    val id: String
) : PoolData

@Serializable
@SerialName("room_pool")
data class TerminalPoolData(
    val rooms: Map<String, Double>
) : PoolData

@Serializable
@SerialName("union_pool")
data class UnionPoolData(
    val pools: List<PoolData>
) : PoolData

@Serializable
sealed interface RoomData

@Serializable
@SerialName("normal")
class NormalRoomData() : RoomData

@Serializable
@SerialName("multi")
class MultiRoomData() : RoomData