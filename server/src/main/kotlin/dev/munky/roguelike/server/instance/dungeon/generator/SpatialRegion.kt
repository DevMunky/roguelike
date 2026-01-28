package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.server.interact.Region
import java.util.Collections
import java.util.LinkedList
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

open class SpatialRegion(
    private val minY: Int? = -64,
    private val maxY: Int? = 319,
    private val stats: Generator.Stats? = null,
) {
    // Transient spatial index: chunk index -> regions residing in that chunk
    private val bin = HashMap<Long, LinkedList<Region>>()

    fun isIntersecting(
        candidate: Region
    ): Boolean {
        val chunks = candidate.containedChunks()
        stats?.chunksChecked += chunks.size
        for (c in chunks) {
            val possible = bin[c] ?: continue
            for (r in possible) {
                stats?.intersectionsChecked++
                if (r.intersectsAabb(candidate)) {
                    stats?.intersections++
                    return true
                }
            }
        }
        return false
    }

    fun isInHeightBounds(area: Region): Boolean {
        if (minY == null || maxY == null) return true
        val bb = area.boundingBox
        val regionMinY = bb.min.y()
        val regionMaxY = bb.max.y()
        val inBounds = (regionMinY >= minY && maxY >= regionMaxY)
        if (!inBounds) stats?.heightBoundsFails++
        return inBounds
    }

    fun index(bounds: Region) {
        for (chunk in bounds.containedChunks()) {
            bin.getOrPut(chunk, ::LinkedList).add(bounds)
        }
    }

    fun unindex(bounds: Region) {
        for (chunk in bounds.containedChunks()) {
            bin[chunk]?.remove(bounds)
        }
    }

    fun ensureReady() {
        bin.clear()
    }
}