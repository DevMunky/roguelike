package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.server.interact.Region
import java.util.Collections
import java.util.LinkedList
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator
import kotlin.math.max

open class SpatialRegion(
    private val minY: Int? = -64,
    private val maxY: Int? = 319,
) {
    private var stats: Generator.Stats? = null
    // Transient spatial index: chunk index -> regions residing in that chunk
    private val bin = HashMap<Long, LinkedList<Region>>()

    fun isIntersecting(
        candidate: Region
    ): Boolean {
        val chunks = candidate.containedChunks()
        stats?.chunksChecked += chunks.size
        for (c in chunks) {
            val possible = bin[c] ?: continue
            stats?.spatialBinAccesses++
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
            stats?.spatialBinAccesses++
            stats?.spatialBinMaxSize = max(stats?.spatialBinMaxSize ?: 0, bin.size.toLong())
        }
    }

    fun unindex(bounds: Region) {
        for (chunk in bounds.containedChunks()) {
            bin[chunk]?.remove(bounds)
            stats?.spatialBinAccesses++
        }
    }

    fun ensureReady(stats: Generator.Stats?) {
        this.stats = stats
        bin.clear()
    }
}