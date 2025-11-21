package dev.munky.roguelike.server.instance.town

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.interact.Conversation
import dev.munky.roguelike.server.interact.NpcPlayer
import dev.munky.roguelike.server.interact.TalkingInteractable
import dev.munky.roguelike.server.interact.conversation
import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ai.EntityAIGroupBuilder
import net.minestom.server.entity.ai.target.ClosestEntityTarget

object TownRenderer : Renderer {
    override suspend fun RenderContext.render() {
        val player = require(RenderKey.Player)
        val town = player.instance as? TownInstance ?: return
        val npc = TestNpc()

        npc.isAutoViewable = false
        npc.setInstance(town, Pos(5.0, .0, 5.0))
        npc.addViewer(player)

        watchAndRequire(StateKey) {
            when (it) {
                State.TALKING -> {
                    npc.lookAt(player)
                    player.fieldViewModifier = 2f
                }
                else -> {
                    player.fieldViewModifier = 1f
                }
            }
        }

        onDispose {
            npc.remove()
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

    class TestNpc : NpcPlayer("test"), TalkingInteractable {
        init {
            aiGroups.clear()
            val group = EntityAIGroupBuilder()
                .addTargetSelector(ClosestEntityTarget(this, 5.0) { it is RoguelikePlayer })
                .build()
            addAIGroup(group)
        }

        override val conversation: Conversation = conversation(username.asComponent()) {
            say("hello there".asComponent())
            response("hi".asComponent()) {
                say("How are you".asComponent())
                response("bad".asComponent()) {
                    execute {
                        it.kick("You aren't doing well.")
                    }
                }
                response("good".asComponent()) {
                    say("That's great to hear!".asComponent())
                }
            }
        }
    }
}