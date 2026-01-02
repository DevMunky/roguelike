package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.item.attack.command.AttackCommand
import dev.munky.roguelike.server.player.RoguePlayer


class AttackContext(
    val instance: RogueInstance,
    val player: RoguePlayer,
) {
    val actions = ArrayList<AttackCommand>()

    /**
     * Suspending as attacks can linger.
     */
    suspend fun attack() {
        for (action in actions) action.execute(instance, player)
    }
}