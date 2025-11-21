package dev.munky.roguelike.server.interact

import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.PlayerEvent

interface Interactable {
    fun onInteract(player: RoguelikePlayer)

    companion object {
        val EVENT_NODE: EventNode<PlayerEvent> = EventNode.type("roguelike:interactable", EventFilter.PLAYER)

        fun registerEvents() {
            EVENT_NODE.addListener(PlayerEntityInteractEvent::class.java) {
                tryInteract(it.player, it.target)
            }
            EVENT_NODE.addListener(PlayerSpawnEvent::class.java) {
                tryInteract(it.player, it.instance)
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }

        private fun tryInteract(player: Player, other: Any) {
            (other as? Interactable)?.onInteract(player as? RoguelikePlayer ?: return)
        }
    }
}