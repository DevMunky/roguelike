package dev.munky.roguelike.server.raycast
import net.minestom.server.collision.BoundingBox
import net.minestom.server.collision.Shape
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.block.BlockIterator
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign


/**
 * A ray that can check for collisions along it.
 *
 *
 * You should construct a Ray using [.Ray].
 * @param origin the ray's origin
 * @param direction the ray's normalized direction
 * @param distance the maximum distance the ray will check
 * @param inverse the cached inverse of the ray
 */
data class Ray(
    val origin: Point,
    val direction: Vec,
    val distance: Double,
    val inverse: Vec
) {
    /**
     * Constructs a ray.
     * @param origin the origin point
     * @param vector the ray's path, which can have any nonzero length
     */
    constructor(
        origin: Point,
        direction: Vec
    ) : this(origin, direction.normalize(), direction.length(), Vec.ONE.div(direction.normalize())) {
        require(!direction.isZero) { "Ray may not have zero length" }
    }

    /**
     * An intersection found between a [Ray] and object of type [T].
     * @param T the type of object collided with
     * @param t the distance along the ray that the intersection was found
     * @param point the point of intersection
     * @param normal the normal of the intersected surface
     * @param exitT the distance along the ray that the ray exits the object
     * @param exitPoint the point from which the ray exits the object
     * @param exitNormal the normal of the surface through which the ray exits
     * @param value the object collided with
    */
    data class Intersection<T>(
        val t: Double,
        val point: Point,
        val normal: Vec,
        val exitT: Double,
        val exitPoint: Point,
        val exitNormal: Vec,
        val value: T
    ) : Comparable<Intersection<*>> {
        /**
         * Compares this intersection's t value with that of another one. If they are equal, compares their exitT values.
         * @param other Any other intersection
         * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
         */
        override fun compareTo(other: Intersection<*>): Int {
            return if (t != other.t) sign(t - other.t).toInt() else sign(exitT - other.exitT).toInt()
        }

        fun <R> withValue(other: R): Intersection<R> {
            return Intersection(t, point, normal, exitT, exitPoint, exitNormal, other)
        }

        /**
         * Returns whether an intersection overlaps with another; if one's [Intersection.exitT] is less than or equal to the other's [Intersection.t].
         *
         *
         * Use this to validate before using [Intersection.merge].
         * @param other the other intersection
         * @return whether the intersections overlap
         */
        fun overlaps(other: Intersection<*>): Boolean {
            return !(other.exitT < t || exitT < other.t)
        }

        /**
         * Merges two intersections by making one out of the lowest t and highest exitT from the intersections.
         * @param other the other intersection
         * @return a potentially larger intersection with the same [.object] as this
         */
        fun merge(other: Intersection<*>): Intersection<T> {
            val startsFirst = t < other.t
            val endsLast = exitT >= other.exitT
            return Intersection(
                if (startsFirst) t else other.t,
                if (startsFirst) point else other.point,
                if (startsFirst) normal else other.normal,
                if (endsLast) exitT else other.exitT,
                if (endsLast) exitPoint else other.exitPoint,
                if (endsLast) exitNormal else other.exitNormal,
                value
            )
        }
    }

    /**
     * Check if this ray hits some shape.
     * @param shape the shape to check against
     * @param offset an offset to shift the shape by, e.g. for block hitboxes
     * @return an [Intersection] if one is found between this ray and the shape, and null otherwise
     * @param <S> any Shape
    </S> */
    fun <S : Shape> cast(shape: S, offset: Vec): Intersection<S>? {
        val bMin: Vec = shape.relativeStart().asVec().sub(origin).add(offset)
        val bMax: Vec = shape.relativeEnd().asVec().sub(origin).add(offset)
        val v1 = bMin.mul(inverse)
        val v2 = bMax.mul(inverse)

        var tN = min(v1.x(), v2.x())
        var tF = max(v1.x(), v2.x())
        tN = max(tN, min(v1.y(), v2.y()))
        tF = min(tF, max(v1.y(), v2.y()))
        tN = max(tN, min(v1.z(), v2.z()))
        tF = min(tF, max(v1.z(), v2.z()))

        if (tF >= tN && tF >= 0 && tN <= distance) {
            return Intersection(
                tN,
                origin.add(direction.mul(tN)),
                Vec(
                    (-(if (v1.x() == tN) 1 else 0) + (if (v2.x() == tN) 1 else 0)).toDouble(),
                    (-(if (v1.y() == tN) 1 else 0) + (if (v2.y() == tN) 1 else 0)).toDouble(),
                    (-(if (v1.z() == tN) 1 else 0) + (if (v2.z() == tN) 1 else 0)).toDouble()
                ),
                tF,
                origin.add(direction.mul(tF)),
                Vec(
                    (-(if (v1.x() == tF) 1 else 0) + (if (v2.x() == tF) 1 else 0)).toDouble(),
                    (-(if (v1.y() == tF) 1 else 0) + (if (v2.y() == tF) 1 else 0)).toDouble(),
                    (-(if (v1.z() == tF) 1 else 0) + (if (v2.z() == tF) 1 else 0)).toDouble()
                ),
                shape
            )
        }

        return null
    }

    /**
     * Check if this ray hits some shape.
     *
     *
     * If you're checking an [Entity], use [Ray.cast] with its position as a vector.
     * @param shape the shape to check against
     * @return an [Intersection] if one is found between this ray and the shape, and null otherwise
     * @param <S> any Shape - for example, a [BoundingBox]
    </S> */
    fun <S : Shape> cast(shape: S): Intersection<S>? {
        return cast(shape, Vec.ZERO)
    }

    /**
     * Get an **unordered** list of collisions with shapes.
     *
     *
     * If you need to know which collisions happened first, use [.castSorted] or [Collections.min].
     * @param shapes the shapes to check against
     * @return a list of results, possibly empty
     * @param <S> any Shape - for example, an [Entity] or [BoundingBox]
    </S> */
    fun <S : Shape> cast(shapes: Collection<S>): MutableList<Intersection<S>?> {
        val result = ArrayList<Intersection<S>?>(shapes.size)
        for (e in shapes) {
            val r = cast(e)
            if (r != null) result.add(r)
        }
        return result
    }

    /**
     * Get an ordered list of collisions with shapes, starting with the closest to the ray origin.
     * @param shapes the shapes to check against
     * @return a list of results, possibly empty
     * @param <S> any Shape - for example, a [BoundingBox]
     */
    fun <S : Shape> castSorted(shapes: Collection<S>): MutableList<Intersection<S>> {
        val result = ArrayList<Intersection<S>>(shapes.size)
        for (e in shapes) {
            val r = cast(e)
            if (r != null) result.add(r)
        }
        result.sort()
        return result
    }

    /**
     * Get the closest collision to the ray's origin.
     * @param shapes the shapes to check against
     * @return the closest result or null if there is none
     * @param <S> any Shape - for example, a[BoundingBox]
    </S> */
    fun <S : Shape> findFirst(shapes: Collection<S>): Intersection<S>? {
        val result = ArrayList<Intersection<S>>(shapes.size)
        for (e in shapes) {
            val r = cast(e)
            if (r != null) result.add(r)
        }
        if (result.isEmpty()) return null
        return result.min()
    }

    /**
     * Get an **unordered** list of collisions with entities.
     *
     *
     * If you need to know which collisions happened first, use [.entitiesSorted] or [Collections.min].
     * @param entities the entities to check against
     * @return a list of results, possibly empty
     * @param <E> any Entity - if you're using [net.minestom.server.instance.EntityTracker], you might use [net.minestom.server.entity.Player]
    </E> */
    fun <E : Entity> entities(entities: Collection<E>): MutableList<Intersection<E>> {
        val result = ArrayList<Intersection<E>>(entities.size)
        for (e in entities) {
            val r = cast(e, e.getPosition().asVec())
            if (r != null) result.add(r)
        }
        return result
    }

    /**
     * Get an ordered list of collisions with entities, starting with the closest to the ray origin.
     * @param entities the entities to check against
     * @return a list of results, possibly empty
     * @param <E> any Entity - if you're using [net.minestom.server.instance.EntityTracker], you might use [net.minestom.server.entity.Player]
    </E> */
    fun <E : Entity> entitiesSorted(entities: Collection<E>): MutableList<Intersection<E>> {
        val result = ArrayList<Intersection<E>>(entities.size)
        for (e in entities) {
            val r = cast(e, e.getPosition().asVec())
            if (r != null) result.add(r)
        }
        result.sort()
        return result
    }

    /**
     * Gets a [BlockIterator] along this ray.
     * @return a [BlockIterator]
     */
    fun blockIterator(): BlockIterator {
        return BlockIterator(origin.asVec(), direction, 0.0, distance)
    }

    /**
     * Gets a [BlockFinder] along this ray.
     *
     *
     * This is useful if you need only the first hit point, for instance, as it does not perform merging.
     * @param blockGetter the provider for blocks, such as an [net.minestom.server.instance.Instance] or [net.minestom.server.instance.Chunk]
     * @return a [BlockFinder]
     */
    fun findBlocks(blockGetter: Block.Getter): BlockFinder {
        return BlockFinder(this, blockIterator(), blockGetter, BlockFinder.BLOCK_HITBOXES)
    }

    /**
     * Gets a [BlockFinder] along this ray.
     *
     *
     * This is useful if you need only the first hit point, for instance, as it does not perform merging.
     * @param blockGetter the provider for blocks, such as an [net.minestom.server.instance.Instance] or [net.minestom.server.instance.Chunk]
     * @param hitboxGetter a function that gets bounding boxes from a block
     *
     *
     * [BlockFinder] provides some options, and [BlockFinder.BLOCK_HITBOXES] is the default.
     * @return a [BlockFinder]
     */
    fun findBlocks(
        blockGetter: Block.Getter,
        hitboxGetter: (Block) -> List<BoundingBox>
    ): BlockFinder {
        return BlockFinder(this, blockIterator(), blockGetter, hitboxGetter)
    }

    /**
     * Gets a [BlockQueue] along this ray.
     *
     *
     * These can perform merging. They are useful if you need exit points from blocks.
     * @param blockGetter the provider for blocks, such as an [net.minestom.server.instance.Instance] or [net.minestom.server.instance.Chunk]
     * @return a [BlockQueue]
     */
    fun blockQueue(blockGetter: Block.Getter): BlockQueue {
        return BlockQueue(findBlocks(blockGetter))
    }

    /**
     * Gets a [BlockQueue] along this ray.
     *
     *
     * These can perform merging. They are useful if you need exit points from blocks.
     * @param blockGetter the provider for blocks, such as an [net.minestom.server.instance.Instance] or [net.minestom.server.instance.Chunk]
     * @param hitboxGetter a function that gets bounding boxes from a block
     *
     *
     * [BlockFinder] provides some options, and [BlockFinder.BLOCK_HITBOXES] is the default.
     * @return a [BlockQueue]
     */
    fun blockQueue(
        blockGetter: Block.Getter,
        hitboxGetter: (Block) -> List<BoundingBox>
    ): BlockQueue {
        return BlockQueue(findBlocks(blockGetter, hitboxGetter))
    }

    /**
     * Gets the end point of this ray with some data that may or may not be useful.
     * @return the end point as a result
     */
    fun endPoint(): Intersection<Ray> {
        return Intersection(
            distance,
            origin.add(direction.mul(distance)),
            direction.neg(),
            distance,
            origin.add(direction.mul(distance)),
            direction,
            this
        )
    }
}