package dev.munky.roguelike.common.renderdispatcherapi

/**
 * Create a RenderDispatch with [RenderDispatch.Companion.with].
 *
 * Common render keys are in [RenderKeys].
 *
 * Specific ones should be declared in the respective [Renderer].
 */
class RenderDispatch private constructor() {
    private val _data = mutableMapOf<RenderContext.Key<*>, Any>()
    val data get() = _data.toMap()

    fun <E : Any> with(key: RenderContext.Key<E>, value: E): RenderDispatch = apply {
        _data[key] = value
    }

    fun with(e: RenderContext.Element): RenderDispatch = apply {
        _data[e.key] = e
    }

    fun dispatch(): RenderHandle = RenderDispatcher.dispatch(this)

    companion object {
        fun <T: Renderer> with(e: T): RenderDispatch = RenderDispatch().with(e)
    }
}