package dev.munky.roguelike.common

import java.util.*

class WeightedRandomList<T>() : Collection<T> {
    constructor(
        initial: Map<T, Number>
    ) : this() {
        for ((key, value) in initial) put(key, value.toDouble())
    }

    private inner class Item(
        val value: T,
        val weight: Double
    )

    private var elements: MutableList<Item> = ArrayList<Item>()

    private var accumulatedWeight = 0.0

    override val size: Int get() = elements.size
    override fun contains(element: T): Boolean = elements.any { it.value == element }
    override fun iterator(): Iterator<T> = elements.map { it.value }.iterator()
    override fun isEmpty(): Boolean = elements.isEmpty()
    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }

    fun put(value: T, weight: Double) {
        accumulatedWeight += weight
        elements += Item(value, accumulatedWeight)
        elements = elements.sortedBy { it.weight }.toMutableList()
    }

    fun weightedRandom(random: Random): T {
        val r = random.nextDouble() * accumulatedWeight

        for (entry in elements) {
            if (entry.weight >= r) {
                return entry.value
            }
        }

        throw NoSuchElementException()
    }
}