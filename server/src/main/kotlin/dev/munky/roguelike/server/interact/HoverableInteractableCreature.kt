package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.Initializable
import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.raycast.Ray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.EntityType
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode

abstract class HoverableInteractableCreature(entityType: EntityType) : InteractableCreature(entityType) {
    open val range: Double = 10.0

    open fun onHoverStart(player: RoguePlayer) {}
    open fun onHoverEnd(player: RoguePlayer) {}

    companion object : Initializable {
        const val MAX_RANGE = 20.0
        val EVENT_NODE = EventNode.type("${Roguelike.NAMESPACE}:interactable.hoverable", EventFilter.PLAYER)

        override suspend fun initialize() {
            Dispatchers.Default.launch {
                while (isActive) {
                    delay(100)
                    for (player in MinecraftServer.getConnectionManager().onlinePlayers.filterIsInstance<RoguePlayer>()) {
                        val dir = player.position.direction().mul(MAX_RANGE)
                        val ray = Ray(player.position.withY { y -> y + player.eyeHeight }, dir)
                        val instance = player.instance ?: continue
                        val hoverable = instance.getNearbyEntities(player.position, MAX_RANGE).filterIsInstance<HoverableInteractableCreature>()
                        val hovering = ray.entitiesSorted(hoverable).firstOrNull()?.value
                        if (hovering == null) {
                            player.hoveredInteractable?.onHoverEnd(player)
                            player.hoveredInteractable = null
                            continue
                        }
                        if (hovering.range * hovering.range < player.position.distanceSquared(hovering.position)) {
                            player.hoveredInteractable?.onHoverEnd(player)
                            player.hoveredInteractable = null
                            continue
                        }
                        if (player.hoveredInteractable == hovering) continue
                        player.hoveredInteractable?.onHoverEnd(player)
                        player.hoveredInteractable = hovering
                        hovering.onHoverStart(player)
                    }
                }
            }
        }
    }
}