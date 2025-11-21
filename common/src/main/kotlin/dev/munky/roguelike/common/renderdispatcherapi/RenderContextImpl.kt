package dev.munky.roguelike.common.renderdispatcherapi

import dev.munky.roguelike.common.logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@Suppress("UNCHECKED_CAST")
internal data class RenderContextImpl(
    override val coroutineContext: CoroutineContext
): InternalRenderContext {
    constructor(
        initialData: Map<RenderContext.Key<*>, *>,
        coroutineContext: CoroutineContext
    ) : this(coroutineContext) {
        for((key, value) in initialData) data[key] = createFlow(value)
    }

    override var rawHandle: Int = 0
    private val data = ConcurrentHashMap<RenderContext.Key<*>, MutableSharedFlow<*>>()
    private var disposer: (suspend () -> Unit)? = null

    // Both values and collectors are stored in the MutableSharedFlow
    private fun <T> createFlow(value:T): MutableSharedFlow<T> = MutableSharedFlow<T>(
        // We need a replay cache to 'get' without 'watch'.
        1,
        // If the replay cache is full (1) and we are emitting a new value, drop
        // the current value in cache and replace it.
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply {
        // Emit ASAP because this is some new state that should be accounted for.
        tryEmit(value)
    }

    private fun <T> getFlow(key: RenderContext.Key<T>): MutableSharedFlow<T?> = data.computeIfAbsent(key) { createFlow(null) } as MutableSharedFlow<T?>

    private fun <T> getFlow(key: RenderContext.StableKey<T>): MutableSharedFlow<T> = data.computeIfAbsent(key) { createFlow(key.default) } as MutableSharedFlow<T>

    override operator fun <T> get(key: RenderContext.Key<T>): T? = data[key]?.replayCache?.firstOrNull() as T?

    override operator fun <T> get(key: RenderContext.StableKey<T>): T = get(key as RenderContext.Key<T>) ?: key.default

    override operator fun <T> set(key: RenderContext.Key<T>, value: T): RenderContext {
        getFlow(key).tryEmit(value ?: if (key is RenderContext.StableKey<T>) key.default else null)
        return this
    }

    override fun <T> require(key: RenderContext.Key<T>): T = get(key) ?: error("Key '$key' was required for rendering yet was not present in render context.")

    override fun onDispose(block: suspend () -> Unit) {
        if (disposer != null) error("Multiple disposal blocks declared, condense them to one block.")
        disposer = block
    }

    /**
     * If the plugin is disabling, run the disposer in an event loop on this thread, finalizing upon completion.
     *
     * Otherwise, launch the disposer in this CoroutineContext, finalizing upon the [Job]'s completion.
     */
    override fun dispose() {
        suspend fun runDisposer() {
            try {
                disposer?.invoke()
            } catch (t: Throwable) {
                LOGGER.error("Exception caught disposing render context ${this@RenderContextImpl}.", t)
            }
        }

        if (rawHandle != -1) {
            RenderDispatcher.dispose(rawHandle)
            rawHandle = -1
        }

        if (!startDisposalsBlocking) {
            launch {
                runDisposer()
            }.invokeOnCompletion { finalize() }
            return
        }

        runBlocking {
            runDisposer()
        }
        finalize()
    }

    // Only invoked from the disposal block.
    private fun finalize() {
        cancel(CancellationException("Render context is disposed."))
        check(this.coroutineContext[Job.Key]?.isCancelled ?: true) { "Render coroutine context was not immediately cancelled." }
    }

    // All watch functions drop the initial value to prevent instant rerendering.
    override fun <T> watch(key: RenderContext.Key<T>, collector: FlowCollector<T?>): Job = launch {
        try {
            getFlow(key).drop(1).collect(collector)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            LOGGER.error("Exception caught watching key $key in render context ${this@RenderContextImpl}.", t)
        }
    }

    // These two methods look similar, but the only difference is one emits null values and one does not.
    // Adding checks for nullability doesn't seem worth it.
    override fun <T> watch(key: RenderContext.StableKey<T>, collector: FlowCollector<T>): Job = launch {
        getFlow(key).drop(1).collect(collector)
    }

    override fun <T> watchAndRequire(key: RenderContext.Key<T>, collector: FlowCollector<T>) = watch(key) {
        // ignore null values if the collector requires a non-null value.
        // kind of impossible to throw to the set() caller, and cant
        // throw in the collector.
        if (it != null) collector.emit(it)
    }

    override fun toString(): String {
        val sb = StringBuilder("RenderContext{")
        for ((key, value) in data) {
            sb.append(key.toString())
                .append('=').append(value.replayCache.firstOrNull())
                .append(',')
        }
        if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }

    companion object {
        private val LOGGER = logger {}
    }
}