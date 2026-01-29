package dev.munky.roguelike.server.instance.dungeon.roomset.room

import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.EnemyFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.JigsawData
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomFeatures
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.RoomJoinFeature
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.rotate
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
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
import org.joml.Vector3d
import java.util.ArrayList
import java.util.HashSet

/**
 * A blueprint for a room derived from a roomset file and its referenced structure file.
 *
 * Contains many utilities for generating rooms and getting details about them,
 * like where connections are and enemies that spawn in them.
 *
 * @see featuresWith
 * @see boundsWith
 */
sealed class RoomBlueprint<D : RoomData>(
    val id: String,
    val roomSet: RoomSet,
    val data: D,
) {
    /**
     * Must not be accessed before [initialized][initialize].
     */
    protected lateinit var featureCache: Array<RoomFeatures>

    /**
     * Must not be accessed before [initialized][initialize].
     */
    protected lateinit var regionCache: Array<Region.Cuboid>

    /**
     * Whether this room is terminal and no connections can be made from it.
     *
     * Must not be accessed before [initialized][initialize].
     */
    var isTerminal: Boolean = false
        private set

    /**
     * Whether this room is the root room of the owning roomset.
     */
    val isRoot = id == roomSet.rootRoomId

    /**
     * The size this room's bounding box shall occupy.
     *
     * This [size] is used for calculating rotations of blocks, so care should be taken
     * to ensure its accuracy.
     */
    protected abstract val size: Point

    /**
     * Sets blocks for this room at a position and does __not__ load chunks.
     *
     * @param instance the instance to set blocks in
     * @param x the minimum x coordinate of the bounds
     * @param y the minimum y coordinate of the bounds
     * @param z the minimum z coordinate of the bounds
     * @param rotation the rotation that must be applied to all blocks
     * @param override the block to override all blocks to be pasted
     */
    protected abstract suspend fun setBlocksUnsafe(
        instance: Instance,
        x: Int,
        y: Int,
        z: Int,
        rotation: Rotation,
        override: Block? = null
    )

    /**
     * Not in a hot path, as results are cached and computations are done during initialization.
     *
     * @see featureFromBlock
     *
     * @see featureCache
     * @see featuresWith
     */
    protected abstract fun computeFeaturesWith(rotation: Rotation) : RoomFeatures

    /**
     * Subclass initialization.
     */
    protected abstract suspend fun initialize0()

    suspend fun initialize() {
        initialize0()
        // compute caches
        featureCache = Array(Rotation.entries.size) { computeFeaturesWith(Rotation.entries[it]) }
        regionCache = Array(Rotation.entries.size) { computeRegionWith(Rotation.entries[it]) }

        isTerminal = featuresWith(Rotation.NONE).connections.size <= 1
    }

    /**
     * @return The bounds of this room, oriented with the given [rotation].
     */
    fun boundsWith(position: Point, rotation: Rotation) : Region.Cuboid =
        regionCache[rotation.ordinal].offset(position.toJoml()) as Region.Cuboid

    /**
     * @return The features of this room, oriented with the given [rotation].
     */
    fun featuresWith(rotation: Rotation) : RoomFeatures = featureCache[rotation.ordinal]

    /**
     * Pastes this room blueprint into an [instance], at a [location][at], with a [rotation].
     *
     * May override all blocks to be pasted with [override], to support clearing / removing rooms.
     *
     * @return The region this room blueprint was pasted into.
     */
    suspend fun paste(instance: Instance, at: Point, rotation: Rotation = Rotation.NONE, override: Block? = null) : Region.Cuboid {
        val bounds = boundsWith(at, rotation)
        val min = bounds.min

        loadChunks(instance, bounds.containedChunks())

        setBlocksUnsafe(instance, min.x().toInt(), min.y().toInt(), min.z().toInt(), rotation, override)
        return bounds
    }

    private suspend fun loadChunks(instance: Instance, chunks: LongArray) {
        // No need to allocate a list of jobs to await, since
        // the Iterable<Job>.joinAll() method just does 'forEach(Job::join)' in the first place.
        for (chunk in chunks) instance.loadChunk(
            CoordConversion.chunkIndexGetX(chunk),
            CoordConversion.chunkIndexGetZ(chunk)
        ).await()
    }

    protected fun computeRegionWith(rotation: Rotation) : Region.Cuboid {
        val half = rotatedSize(rotation, size).div(2.0).toJoml()
        val min = Vector3d(.0).sub(half)
        val max = Vector3d(.0).add(half)
        return Region.Cuboid(min, max)
    }


    companion object {
        protected fun featureFromBlock(roomSet: RoomSet, block: Block, position: Point, nbt: CompoundBinaryTag?, rotation: Rotation, size: Point) : JigsawData? {
            nbt ?: return null
            if (!block.compare(Block.JIGSAW, Block.Comparator.ID)) return null

            // Rotate the local position into the rotated local space (origin at min corner),
            // then convert to an offset relative to the center of the rotated structure.
            val localRot = rotateAboutCenter(position, rotation, size)
            val offsetFromCenter = localToCenter(localRot, rotation, size)

            val name = nbt.getString("name")
            val pool = nbt.getString("pool")
            val target = nbt.getString("target")

            val finalBlockData = nbt.getString("final_state")
            val finalBlock = Block.fromState(finalBlockData)
                ?: error("Invalid block data '$finalBlockData' for feature '$name' ($target) at schematic location $position.")

            // minecraft always exports orientation for jigsaw blocks.
            val baseDir = directionFromOrientation(block.getProperty("orientation")!!)
            val dir = rotation.rotate(baseDir)

            return when (target) {
                // For backwards compatibility, as older roomsets do not explicitly
                // define a target in the jigsaw block, resulting in 'minecraft:empty'
                ConnectionFeature.ID, "minecraft:empty" -> ConnectionFeature(
                    name = name,
                    poolName = pool,
                    pool = roomSet.pools[pool],
                    finalBlock = finalBlock,
                    position = offsetFromCenter,
                    direction = dir
                )
                EnemyFeature.ID -> EnemyFeature(
                    name = name,
                    poolName = pool,
                    pool = roomSet.pools[pool],
                    finalBlock = finalBlock,
                    position = offsetFromCenter,
                    direction = dir
                )
                RoomJoinFeature.ID -> RoomJoinFeature(
                    name = name,
                    poolName = pool,
                    finalBlock = finalBlock,
                    position = offsetFromCenter,
                    direction = dir
                )
                else -> error("Invalid feature target '$target' for feature '$name' at schematic location $position.")
            }
        }

        /**
         * Size of the structure after applying rotation (x/z swapped for 90/270).
         */
        protected fun rotatedSize(rotation: Rotation, size: Point): Point = when (rotation) {
            Rotation.NONE, Rotation.CLOCKWISE_180 -> size
            Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_270 -> Vec(size.z(), size.y(), size.x())
        }

        /**
         * Convert a rotated local-space position (min-corner origin) into an offset from the center
         * of the rotated structure. Returned as BlockVec for consistency with existing API.
         */
        protected fun localToCenter(localRot: BlockVec, rotation: Rotation, size: Point): BlockVec {
            val half = rotatedSize(rotation, size).div(2.0)
            return localRot.sub(half).asBlockVec()
        }

        /**
         * Derives a [net.minestom.server.utils.Direction] from an orientation property. Fallback is [net.minestom.server.utils.Direction.DOWN], which is invalid for most features and ignored.
         */
        protected fun directionFromOrientation(orientation: String) : Direction {
            return when (orientation) {
                "north_up" -> Direction.NORTH
                "east_up" -> Direction.EAST
                "south_up" -> Direction.SOUTH
                "west_up" -> Direction.WEST
                else -> if (orientation.startsWith("up")) Direction.UP else Direction.DOWN
            }
        }

        /**
         * Rotate a local block position around the center of the structure's Y axis (integer grid preserving),
         * returning a BlockVec in the rotated local space (origin at min corner of rotated AABB).
         */
        protected fun rotateAboutCenter(pos: Point, rotation: Rotation, size: Point): BlockVec {
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

        protected fun computeFeaturesWithStructure(
            roomSet: RoomSet,
            structure: Structure,
            rotation: Rotation
        ) : RoomFeatures {
            if (structure.blocks.isEmpty()) return RoomFeatures.EMPTY
            val palette = structure.palettes.firstOrNull() ?: return RoomFeatures.EMPTY

            val connections = ArrayList<ConnectionFeature>()
            val enemies = ArrayList<EnemyFeature>()
            val joins = ArrayList<RoomJoinFeature>()

            for (bi in structure.blocks) {
                val block = palette[bi.paletteIndex]

                val nbt = bi.blockEntity?.data ?: continue

                val feature = featureFromBlock(roomSet, block, bi.pos, nbt, rotation, structure.size) ?: continue

                when (feature) {
                    is ConnectionFeature -> connections.add(feature)
                    is EnemyFeature -> enemies.add(feature)
                    is RoomJoinFeature -> joins.add(feature)
                }
            }

            return RoomFeatures(connections.toTypedArray(), enemies.toTypedArray(), joins.toTypedArray())
        }

        protected suspend fun setBlocksStructureUnsafe(instance: Instance, structure: Structure, x: Int, y: Int, z: Int, rotation: Rotation, override: Block? = null) {
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
                    // Debug: Diamond block is the first block
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
                            block =
                                Block.fromState(final) ?: error("Invalid final state $final for jigsaw block ${bi.pos}.")
                        }
                    }
                    // rotated local position, then translate by min
                    val localRot = rotateAboutCenter(bi.pos, rotation, structure.size)
                    val pos: Point = localRot.add(x, y, z)
                    block = block.rotate(rotation)

                    if (override != null) block = override

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
}