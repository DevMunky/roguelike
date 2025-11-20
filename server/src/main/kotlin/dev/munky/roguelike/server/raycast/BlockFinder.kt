package dev.munky.roguelike.server.raycast
import net.minestom.server.collision.BoundingBox
import net.minestom.server.collision.ShapeImpl
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.block.BlockIterator

/**
 * An iterator for collisions along a [Ray] using certain providers for blocks and their hitboxes.
 *
 *
 * Use [Ray.findBlocks] to create.
 *
 *
 * Keep in mind that while the entry points are always accurate, the exit points may not be in blocks like stairs.
 *
 *
 * For these cases, you can manually [check][net.minestom.server.collision.Ray.Intersection.overlaps]
 * and [merge][net.minestom.server.collision.Ray.Intersection.merge], or use a [BlockQueue] instead.
 * @param ray the ray to traverse
 * @param blockIterator the block iterator
 * @param blockGetter
 * @param hitboxGetter
 */
data class BlockFinder(
    val ray: Ray,
    val blockIterator: BlockIterator,
    val blockGetter: Block.Getter,
    val hitboxGetter: (Block) -> List<BoundingBox>
) : MutableIterator<MutableCollection<Ray.Intersection<Block>>> {
    override fun hasNext(): Boolean {
        return blockIterator.hasNext()
    }

    override fun next(): MutableList<Ray.Intersection<Block>> {
        val results = ArrayList<Ray.Intersection<Block>>()
        if (blockIterator.hasNext()) {
            val p = blockIterator.next()
            val b = blockGetter.getBlock(p)
            val hitboxes = hitboxGetter(b)
            if (!hitboxes.isEmpty()) {
                for (h in hitboxes) {
                    val r = ray.cast(h, p.asVec())
                    if (r != null) results += r.withValue(b)
                }
                if (!results.isEmpty()) {
                    results.sort()
                    return results
                }
            }
        }
        return ArrayList()
    }

    /**
     * Return the next closest intersection.
     * Keep in mind that this discards all other hits within the found block.
     * @return the next closest intersection, or null if there are none
     */
    fun nextClosest(): Ray.Intersection<Block>? {
        while (blockIterator.hasNext()) {
            val results = next()
            if (!results.isEmpty()) return results.min()
        }
        return null
    }

    override fun remove() {
        blockIterator.remove()
    }

    companion object {
        /**
         * A hitbox getter that finds a block's collision hitboxes.
         */
        val BLOCK_HITBOXES: (Block) -> List<BoundingBox> = { (it.registry()!!.collisionShape() as ShapeImpl).boundingBoxes() }

        /**
         * A 1x1x1 block hitbox.
         */
        private val CUBE: List<BoundingBox> = listOf(BoundingBox(Vec.ZERO, Vec.ONE))

        /**
         * A hitbox getter that returns a cube if the block has any solid collision.
         */
        val SOLID_CUBE_HITBOXES: (Block) -> List<BoundingBox> = { if (it.isSolid) CUBE else mutableListOf() }

        /**
         * A hitbox getter that returns a cube if the block is not air.
         */
        val CUBE_HITBOXES: (Block) -> List<BoundingBox> = { if (!it.isAir) CUBE else mutableListOf() }
    }
}