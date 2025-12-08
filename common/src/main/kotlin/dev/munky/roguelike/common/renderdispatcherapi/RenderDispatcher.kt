package dev.munky.roguelike.common.renderdispatcherapi

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.common.renderdispatcherapi.InternalRenderContext.Companion.INVALID_HANDLE
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlin.coroutines.CoroutineContext

@Volatile var startDisposalsBlocking = false

/**
 * Manages all context lifetimes, distributes handles, and renders dispatches.
 */
internal object RenderDispatcher {
    private val LOGGER = logger {}
    private val contexts = RenderContextList()

    private val rootScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        LOGGER.error("Internal exception caught executing render dispatch.", throwable)
    })

    @OptIn(InternalCoroutinesApi::class)
    internal fun dispatch(dispatch: RenderDispatch): RenderHandle {
        if (!rootScope.isActive) throw IllegalStateException("Root scope is not active.", rootScope.coroutineContext[Job]!!.getCancellationException())

        val data = dispatch.data
        val renderer = data[Renderer] as Renderer?
        val parent = dispatch.parentContext

        if (parent != null) {
            if (parent !is RenderContextImpl) error("Forking RenderContexts may only be done from within a Renderer.")
            val ctx = RenderContextImpl(parent, dispatch.data)

            val handle = contexts.add(ctx)

            if (renderer != null) {
                parent.onDispose {
                    ctx.dispose()
                }
                ctx.launch {
                    // This is to enable the nice abstract extension function for Renderers.
                    with (renderer) {
                        ctx.render()
                    }
                }
            }

            return handle
        }

        val ctx = RenderContextImpl(
            data,
            rootScope.coroutineContext.plus(Job()) + CoroutineExceptionHandler { ctx, throwable ->
                LOGGER.error("Exception caught rendering.", throwable)
                if (ctx is InternalRenderContext) { // Should be...
                    ctx.dispose()
                    // This is to make sure there aren't any stray contexts.
                }
            })

        val handle = contexts.add(ctx)

        if (renderer != null) ctx.launch {
            // This is to enable the nice abstract extension function for Renderers.
            with (renderer) {
                ctx.render()
            }
        }

        return handle
    }

    internal operator fun get(handle: Int): RenderContext? = if (handle == EMPTY_HANDLE) RenderContext.EMPTY else contexts[handle]

    internal fun dispose(handle: Int) {
        LOGGER.info("Disposing handle {}", contexts.debugHandle(handle))
        contexts.remove(handle)?.dispose0(startDisposalsBlocking)
        // trigger emission on the Disposal flow.
    }

    internal fun debugHandle(handle: Int): String = contexts.debugHandle(handle)

    internal const val EMPTY_HANDLE = -1

    /**
     * Low memory consumption and high-efficiency state management of render contexts.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    private class RenderContextList {
        private var capacity = 128
        private var contexts = Array<InternalRenderContext?>(capacity) { null }
        private var slotCounts = UShortArray(capacity) { 0u }
        private var ci = 0

        fun add(ctx: InternalRenderContext): RenderHandle {
            val index = nextIndex()
            if (index >= capacity) {
                // infinite expansion here is by design.
                // Although, TODO implement shrinking if size drops below a certain factor
                grow()
            }
            contexts[index] = ctx
            val rawHandle = createHandle(index)
            ctx.rawHandle = rawHandle
            return RenderHandle(rawHandle)
        }

        fun remove(handle: Int): InternalRenderContext? {
            if (handle == INVALID_HANDLE) throw IllegalArgumentException("invalid handle")
            val ind = validHandleOf(handle)?.getIndex() ?: return null
            val r = contexts[ind]
            contexts[ind] = null
            // Why decrement slots, it kind of defeats the entire purpose...
            // slotCounts[ind]-- // shouldn't be zero
            return r
        }

        private fun nextIndex() : Int {
            val checkFactor = 0.3f
            var i = 0
            while (ci / capacity.toFloat() >= checkFactor) {
                // start from the bottom as older is more likely to be disposed
                while (true) contexts[i++] ?: break // found
                ci = i - 1
                return ci
            }
            return ci++.let { if (it > UShort.MAX_VALUE.toInt()) error("index out of bounds") else it }
        }

        private fun grow() {
            capacity *= 2
            contexts = contexts.copyOf(capacity)
            slotCounts = slotCounts.copyOf(capacity)
            if (capacity >= 512) {
                LOGGER.warn("Render context list reached capacity of $capacity.")
                LOGGER.warn("Slot counts = ${slotCounts.joinToString(",")}")
                LOGGER.warn("Contexts = ${contexts.joinToString(",") { it?.let { "exists" } ?: "null" }}")
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Int.getIndex(): Int = this and 0x00_00_FF_FF
        @Suppress("NOTHING_TO_INLINE")
        private inline fun Int.getSlotCount(): UShort = (this shr 16).toUShort()

        operator fun get(handle: Int): RenderContext? = contextWrappedOf(handle)

        private fun validHandleOf(handle: Int): Int? {
            val index = handle.getIndex()
            if (slotCounts[index] != handle.getSlotCount()) {
                // got cleared, as the slot count incremented, meaning some other context was placed in this context's slot.
                LOGGER.info("Slot# at ind $index is ${slotCounts[index]} and handle is slot# ${handle.getSlotCount()}. Returned null.")
                return null
            }
            require(index in 0..<capacity) { "Array index out of bounds" }
            if (contexts[index] == null) return null
            return handle
        }

        private fun contextWrappedOf(handle: Int) = validHandleOf(handle)?.let { WrappedRenderContext(it) }

        fun contextImplOf(handle: Int) : InternalRenderContext {
            val h = validHandleOf(handle) ?: error("Invalid handle (${debugHandle(handle)}).")
            val c = contexts[h.getIndex()] ?: error("Context is disposed (${debugHandle(handle)}).")
            if (c is WrappedRenderContext) error("Wrapped contexts should never be indexed in the context list (${debugHandle(handle)}).")
            return c
        }

        private fun createHandle(index: Int): Int {
            val slotCount = slotCounts[index].plus(1u).toUShort()
            if (slotCount + 1u > UShort.MAX_VALUE) error("Slot Count overflow! RenderDispatch Spam?")
            slotCounts[index] = slotCount
            val handle = index or (slotCount.toInt() shl 16)
            return handle
        }

        fun debugHandle(handle: Int): String {
            if (handle == INVALID_HANDLE) return "Handle{INVALID_HANDLE}"
            if (handle == EMPTY_HANDLE) return "Handle{EMPTY_HANDLE}"
            val index = handle.getIndex()
            return "Handle{" +
                    "index=$index," +
                    "slot=${handle.getSlotCount()}}," +
                    "dispatcher{index=${contexts[index]?.let { "exists" } ?: "null"}," +
                    "slot=${slotCounts.getOrNull(index)}}"
        }

        companion object {
            private val LOGGER = logger {}
        }
    }

    // I could ditch RenderHandle entirely, as it has no necessity, and instead return an instance of WrappedRenderContext.
    private class WrappedRenderContext(override var rawHandle: Int) : InternalRenderContext {
        private val ctx: InternalRenderContext get() = contexts.contextImplOf(rawHandle)
        override fun <T> get(key: RenderContext.Key<T>): T? = ctx[key]
        override fun <T> get(key: RenderContext.StableKey<T>): T = ctx[key]
        override fun <T> set(key: RenderContext.Key<T>, value: T): RenderContext = ctx.set(key, value)
        override fun <T> require(key: RenderContext.Key<T>): T = ctx.require(key)
        override fun onDispose(block: suspend () -> Unit) = ctx.onDispose(block)
        override fun dispose() = ctx.dispose()
        override fun dispose0(doEventLoop: Boolean) = ctx.dispose0(doEventLoop)
        override fun <T> watch(key: RenderContext.Key<T>, collector: FlowCollector<T?>): Job = ctx.watch(key, collector)
        override fun <T> watch(key: RenderContext.StableKey<T>, collector: FlowCollector<T>): Job = ctx.watch(key, collector)
        override fun <T> watchAndRequire(key: RenderContext.Key<T>, collector: FlowCollector<T>): Job = ctx.watchAndRequire(key, collector)
        override fun handle(): RenderHandle = ctx.handle()
        override val coroutineContext: CoroutineContext get() = ctx.coroutineContext
    }
}