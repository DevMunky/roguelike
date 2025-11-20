package dev.munky.roguelike.server.raycast

import net.minestom.server.instance.block.Block
import java.util.*
import java.util.function.BiPredicate


/**
 * A modifiable queue for more advanced block raycasts.
 *
 *
 * Use [Ray.blockQueue] to create.
 */
class BlockQueue(private val refiller: MutableIterator<MutableCollection<Ray.Intersection<Block>>>) : ArrayDeque<Ray.Intersection<Block>>() {
    /**
     * Refill this queue with zero or more results.
     * @return number of entries added
     */
    fun refill(): Int {
        if (!refiller.hasNext()) return 0
        val next = refiller.next()
        addAll(next)
        return next.size
    }

    /**
     * Keep refilling until something is added or the refiller cannot add anything more.
     * @return number of entries added, zero if refiller does not have a next element
     */
    fun refillSome(): Int {
        while (refiller.hasNext()) {
            val result = refill()
            if (result > 0) return result
        }
        return 0
    }

    /**
     * Keep refilling until the refiller does not have a next element.
     * @return number of entries added
     */
    fun refillAll(): Int {
        var added = 0
        while (refiller.hasNext()) {
            added += refill()
        }
        return added
    }

    /**
     * If the first and second elements exist and [can merge][net.minestom.server.collision.Ray.Intersection.overlaps],
     * merge them, otherwise do nothing
     * @param predicate a predicate for merging
     * @return whether elements were merged
     */
    /**
     * If the first and second elements exist and [can merge][net.minestom.server.collision.Ray.Intersection.overlaps],
     * merge them, otherwise do nothing
     * @return whether elements were merged
     */
    @JvmOverloads
    fun merge(predicate: BiPredicate<Ray.Intersection<Block>, Ray.Intersection<Block>> = { _: Ray.Intersection<Block>, _: Ray.Intersection<Block> -> true }): Boolean {
        if (isEmpty()) return false
        val first = poll()
        val next = peek()
        if (next == null || !first.overlaps(next) || !predicate.test(first, next)) {
            addFirst(first)
            return false
        }
        remove()
        addFirst(first.merge(next))
        return true
    }

    /**
     * [Merge][.merge] for as long as possible.
     * @param predicate a predicate for merging
     * @return number of times merged
     */
    fun mergeAll(predicate: BiPredicate<Ray.Intersection<Block>, Ray.Intersection<Block>>): Int {
        var merged = 0
        while (merge(predicate)) merged++
        return merged
    }

    /**
     * [Merge][.merge] for as long as possible.
     * @return number of times merged
     */
    fun mergeAll(): Int {
        var merged = 0
        while (merge()) merged++
        return merged
    }
}