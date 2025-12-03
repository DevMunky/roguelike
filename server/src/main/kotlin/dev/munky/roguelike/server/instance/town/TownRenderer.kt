package dev.munky.roguelike.server.instance.town

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.interact.Conversation
import dev.munky.roguelike.server.interact.NpcPlayer
import dev.munky.roguelike.server.interact.conversation
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.coordinate.Pos

object TownRenderer : Renderer {
    override suspend fun RenderContext.render() {
        val player = require(RoguePlayer)
        val town = require(RogueInstance)

        val npc = TestNpc()

        npc.isAutoViewable = false
        npc.setInstance(town, Pos(5.0, .0, 5.0))
        npc.addViewer(player)

        onDispose {
            npc.remove()
        }
    }

    class TestNpc : NpcPlayer("test") {
        override val range = 5.0

        override val conversation: Conversation = conversation(username.asComponent()) {
            interrupt("Alright then.".asComponent())
            say("hello there".asComponent())
            response("hi".asComponent()) {
                interrupt("NOW you don't want to talk?".asComponent())
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