package dev.munky.roguelike.common.renderdispatcherapi

interface Renderer: RenderContext.Element {
    override val key : RenderContext.Key<*> get() = Companion
    suspend fun RenderContext.render()

    companion object : RenderContext.Key<Renderer>
}

/**
 * Interesting suggestion from the chat. Could be interesting, although not super useful with my current design.
 *
 * Essentially context is shared between multiple renders. This approach is called composition.
 *
 * Supposedly Compose utilizes this approach as well as a kind of similar key-value system for context during execution.
 *
 * For example, I could render a zombie and a text display using the same context. This comes with some caveats, such as there
 * would be no way to tell the text display renderer to render slightly offset above in the y-axis in order to have the display appear above the zombie.
 */
fun combine(vararg renderers: Renderer): Renderer = ComposedRenderer(renderers)

operator fun Renderer.plus(other: Renderer) : Renderer = combine(this, other)

class ComposedRenderer(private val renderers: Array<out Renderer>): Renderer {
    override suspend fun RenderContext.render() {
        // This context is propagated to these renderers,
        // meaning disposal here disposes of them as well.
        // TODO investigate race conditions with scope cancellation.
        for (it in renderers) {
            val r = this
            with(it) {
                r.render()
            }
        }
    }
}