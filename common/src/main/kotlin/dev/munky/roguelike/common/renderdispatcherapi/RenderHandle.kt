package dev.munky.roguelike.common.renderdispatcherapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle

/**
 * Borrows [the coroutine scope][CoroutineScope] from the [context], which was created in the [RenderDispatcher], and finally cancels it upon [disposal][dispose].
 *
 * [Renderer] implementations may dispose the [RenderHandle] themselves.
 */
@JvmInline
value class RenderHandle internal constructor(
    private val rawHandle: Int
) : DisposableHandle {
    /**
     * @return The context associated with this handle.
     */
    val context: RenderContext? get() = RenderDispatcher[rawHandle]

    val isDisposed get() = RenderDispatcher[rawHandle] == null

    override fun dispose() = RenderDispatcher.dispose(rawHandle)

    override fun toString(): String = "RenderHandle{${RenderDispatcher.debugHandle(rawHandle)}}"

    companion object {
        val EMPTY = RenderHandle(RenderDispatcher.EMPTY_HANDLE)
    }
}