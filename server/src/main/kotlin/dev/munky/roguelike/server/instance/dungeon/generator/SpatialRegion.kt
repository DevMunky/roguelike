package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.server.interact.Region
import java.util.Collections
import java.util.LinkedList
import java.util.TreeMap
import kotlin.collections.iterator

open class SpatialRegion(
    private val minY: Int? = -64,
    private val maxY: Int? = 319,
    private val stats: Generator.Stats? = null,
) {
    // Transient spatial index: chunk index -> regions residing in that chunk
    private val bin = Collections.synchronizedSortedMap(TreeMap<Long, LinkedList<Region>>())

    fun createSnapshot() : Snapshot {
        val map = LinkedHashMap<Long, LinkedList<Region>>(bin.size)
        for ((k, v) in bin) map[k] = LinkedList(v)
        return Snapshot(map)
    }

    fun isIntersecting(
        chunks: LongArray,
        candidate: Region,
        snapshot: Snapshot
    ): Boolean {
        stats?.chunksChecked += chunks.size
        for (c in chunks) {
            val possible = snapshot[c] ?: continue
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
        return regionMinY >= minY && maxY >= regionMaxY
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

    @JvmInline
    value class Snapshot(private val map: Map<Long, LinkedList<Region>>) : Map<Long, LinkedList<Region>> by map
}