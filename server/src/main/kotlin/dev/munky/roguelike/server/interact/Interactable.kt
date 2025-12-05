package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.Initializable
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.PlayerEvent

interface Interactable {
    /**
     * Invoked for only the player's main hand.
     */
    fun onInteract(player: RoguePlayer)

    companion object : Initializable {
        val EVENT_NODE: EventNode<PlayerEvent> = EventNode.type("${Roguelike.NAMESPACE}:interactable", EventFilter.PLAYER)

        override suspend fun initialize() {
            EVENT_NODE.addListener(PlayerEntityInteractEvent::class.java) {
                if (it.hand != PlayerHand.MAIN) return@addListener
                tryInteract(it.player, it.target)
            }
            EVENT_NODE.addListener(PlayerSpawnEvent::class.java) {
                tryInteract(it.player, it.instance)
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
            InteractableRegion.initialize()
            HoverableInteractableCreature.initialize()
        }

        private fun tryInteract(player: Player, other: Any) {
            (other as? Interactable)?.onInteract(player as? RoguePlayer ?: return)
        }
    }
}