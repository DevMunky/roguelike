package dev.munky.roguelike.common

interface TypedMap<O> where O : TypedMap<O> {
    operator fun <K, V> get(key: K): V? where K : Key<O, V>, V : Any
    operator fun <K> contains(key: K) : Boolean where K : Key<O, *>

    interface Key<O, V>
}

interface MutableTypedMap<O> : TypedMap<O> where O : TypedMap<O> {
    operator fun <K, V> set(key: K, value: V) where K : TypedMap.Key<O, V>, V : Any
    fun <K, V> remove(key: K) : V? where K : TypedMap.Key<O, V>, V : Any

    private class Wrapper<O>(
        private val data: MutableMap<TypedMap.Key<O, *>, Any>
    ) : MutableTypedMap<O> where O : MutableTypedMap<O> {
        @Suppress("UNCHECKED_CAST")
        override operator fun <K, V> get(key: K): V? where K : TypedMap.Key<O, V>, V : Any =
            data[key] as V?

        override fun <K : TypedMap.Key<O, *>> contains(key: K): Boolean =
            data.containsKey(key)

        override operator fun <K, V> set(key: K, value: V) where K : TypedMap.Key<O, V>, V : Any =
            data.set(key, value)

        @Suppress("UNCHECKED_CAST")
        override fun <K : TypedMap.Key<O, V>, V : Any> remove(key: K): V? =
            data.remove(key) as V?
    }

    companion object {
        /**
         * Example usage:
         * ```
         * class Context : MutableTypedMap<Context> by MutableTypedMap.of(ConcurrentHashMap()) {
         *     @JvmInline
         *     value class Key<V>(private val id: String) : TypedMap.Key<Context, V>
         * }
         * ```
         * @param map The map implementation to use. Must be empty to guarantee type safety.
         */
        fun <O : MutableTypedMap<O>> of(
            map: MutableMap<TypedMap.Key<O, *>, Any>
        ) : MutableTypedMap<O> {
            require(map.isEmpty()) { "Map must be empty." }
            return Wrapper(map)
        }
    }
}