package dev.munky.roguelike.server.instance.dungeon.roomset.room

import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomFeatures
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import net.hollowcube.schem.Structure
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class NormalRoomBlueprint(
    id: String,
    parent: RoomSet,
    data: NormalRoomData
) : RoomBlueprint<NormalRoomData>(id, parent, data) {
    /**
     * Must not be used before [initialized][initialize].
     */
    private lateinit var structure: Structure

    /**
     * Must not be used before [initialized][initialize].
     */
    override val size: Point get() = structure.size

    override suspend fun initialize0() {
        try {
            structure = Roguelike.server().structures().getOrThrow(id)
        } catch (t: Throwable) {
            throw RuntimeException("Failed to load structure for room '$id'.", t)
        }
    }

    override fun computeFeaturesWith(rotation: Rotation): RoomFeatures =
        computeFeaturesWithStructure(roomSet, structure, rotation)

    /**
     * Does not load chunks, ensure chunks are loaded before calling.
     */
    override suspend fun setBlocksUnsafe(
        instance: Instance,
        x: Int,
        y: Int,
        z: Int,
        rotation: Rotation,
        override: Block?
    ) = setBlocksStructureUnsafe(instance, structure, x, y, z, rotation, override)
}

class MultipartRoomBlueprint(
    id: String,
    roomset: RoomSet,
    data: MultipartRoomData,
) : RoomBlueprint<MultipartRoomData>(id, roomset, data) {
    /**
     * Must not be used before [initialized][initialize].
     */
    private lateinit var structure: Structure

    override val size: Point
        get() = TODO("Not yet implemented")

    override suspend fun initialize0() {
        try {
            /**
             * Organized as follows:
             * [
             * [structure1, structure2],
             * [structure3, structure4]
             * ]
             *
             * Which forms the 2d arrangement of the structures.
             *
             * Pretty sure this is a column-major matrix.
             */
            val structures = data.structures.map { row ->
                row.map { value -> Roguelike.server().structures().getOrThrow(value) }
            }

            /**
             * See above
             */
            val features = structures.map { row ->
                row.map { value -> computeFeaturesWithStructure(roomSet, value, Rotation.NONE) }
            }

            // Top left (NW) corner of matrix
            // Structures are added first by row then column
            // so all structures in row i are processed before continuing to the column j.
            val origin = BlockVec(0)
            var size: BlockVec = BlockVec.ZERO
            var palette: Array<Block> = emptyArray()
            val blocks = ArrayList<Structure.BlockInfo>()

            var i = 0
            var j = 0
            repeat (structures.size) {
                val row = structures[j]
                repeat (row.size) {
                    // the join features on this sub structure
                    val joins = features[j][i].joins

                    i++
                }
                j++
            }

            structure = Structure(size, blocks, listOf(palette), emptyList())
        } catch (t: Throwable) {
            throw RuntimeException("Failed to load structures for room '$id'.", t)
        }
    }

    override suspend fun setBlocksUnsafe(
        instance: Instance,
        x: Int,
        y: Int,
        z: Int,
        rotation: Rotation,
        override: Block?
    ) = setBlocksStructureUnsafe(instance, structure, x, y, z, rotation, override)

    override fun computeFeaturesWith(rotation: Rotation): RoomFeatures =
        computeFeaturesWithStructure(roomSet, structure, rotation)
}