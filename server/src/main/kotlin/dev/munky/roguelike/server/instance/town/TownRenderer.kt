package dev.munky.roguelike.server.instance.town

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey

object TownRenderer : Renderer {
    override suspend fun RenderContext.render() {
        val player = require(RenderKey.Player)

        watchAndRequire(StateKey) {
            when (it) {
                State.TALKING -> {
                    player.fieldViewModifier = 2f
                }
                else -> {
                    player.fieldViewModifier = 1f
                }
            }
        }
    }

    data object StateKey : RenderContext.StableKey<State> {
        override val default: State = State.NONE
    }

    enum class State {
        TALKING,
        ON_ELEVATOR,
        NONE
    }
}