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
    ) {
        override fun toString(): String = "Item(v=$value, w=$weight)"
    }

    private var _elements: MutableList<Item> = ArrayList<Item>()

    private var accumulatedWeight = 0.0

    val elements: Map<T, Double> get() {
        val dest = HashMap<T, Double>()
        for (e in _elements) {
            dest[e.value] = e.weight
        }
        return dest
    }

    override val size: Int get() = _elements.size
    override fun contains(element: T): Boolean = _elements.any { it.value == element }
    override fun iterator(): Iterator<T> = _elements.map { it.value }.iterator()
    override fun isEmpty(): Boolean = _elements.isEmpty()
    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }

    fun put(value: T, weight: Double) {
        accumulatedWeight += weight
        _elements += Item(value, accumulatedWeight)
        _elements = _elements.sortedBy { it.weight }.toMutableList()
    }

    fun weightedRandom(random: Random): T {
        val r = random.nextDouble() * accumulatedWeight

        for (entry in _elements) {
            if (entry.weight >= r) {
                return entry.value
            }
        }

        throw NoSuchElementException()
    }

    override fun toString(): String {
        return _elements.toString()
    }
}