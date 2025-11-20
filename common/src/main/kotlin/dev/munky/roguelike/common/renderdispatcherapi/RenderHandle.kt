package dev.munky.roguelike.common.renderdispatcherapi

import dev.munky.roguelike.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle

/**
 * Borrows [the coroutine scope][CoroutineScope] from the [context], which was created in the [RenderDispatcher], and finally cancels it upon [disposal][dispose].
 *
 * [Renderer] implementations may dispose the [RenderHandle] themselves.
 */
class RenderHandle internal constructor(
    internal val rawHandle: Int
) : DisposableHandle {
    var isDisposed = false
        private set

    /**
     * @return The context associated with this handle.
     */
    val context: RenderContext
        get() {
            if (isDisposed) error("This handle has been disposed, and the context has been reclaimed ($this).")
            val ctx = RenderDispatcher[rawHandle] ?: error("This handle is invalid ($this).")
            return ctx
        }

    override fun dispose() {
        RenderDispatcher.dispose(rawHandle)
        isDisposed = true
    }

    override fun toString(): String {
        return "RenderHandle{${RenderDispatcher.debugHandle(rawHandle)}}"
    }

    companion object {
        private val LOGGER = logger {}

        val EMPTY = RenderHandle(RenderDispatcher.EMPTY_HANDLE)

        init {
            EMPTY.isDisposed = true
        }
    }
}