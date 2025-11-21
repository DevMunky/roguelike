package dev.munky.roguelike.common.renderdispatcherapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The heart of the RenderDispatcher API.
 *
 * Contains the data pertaining to a rendering operation.
 */

sealed interface RenderContext : CoroutineScope {
    operator fun <T> get(key: Key<T>): T?
    operator fun <T> get(key: StableKey<T>): T

    /**
     * Set a value in this context.
     *
     * @return [this][RenderContext] for chaining.
     */
    @Suppress("KDocUnresolvedReference")
    operator fun <T> set(key: Key<T>, value: T): RenderContext

    fun <T> require(key: Key<T>): T

    /**
     * The function to be invoked when this context is disposed of.
     *
     * @return The [Job] handling all disposals.
     */
    fun onDispose(block: suspend () -> Unit)
    fun dispose()

    fun <T> watch(key: Key<T>, collector: FlowCollector<T?>): Job
    fun <T> watch(key: StableKey<T>, collector: FlowCollector<T>): Job

    /**
     * Simply ignores null values put into this context.
     */
    fun <T> watchAndRequire(key: Key<T>, collector: FlowCollector<T>): Job

    interface Key<V>

    interface StableKey<V> : Key<V> {
        val default: V
    }

    interface Element {
        val key: Key<*>
    }

    companion object {
        val EMPTY: RenderContext = RenderContextImpl(emptyMap<Key<*>, Any>(), EmptyCoroutineContext + Job())
    }
}

internal interface InternalRenderContext : RenderContext {
    var rawHandle: Int
}