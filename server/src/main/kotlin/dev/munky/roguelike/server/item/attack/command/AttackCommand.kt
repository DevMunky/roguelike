package dev.munky.roguelike.server.item.attack.command

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.LivingEntity

sealed interface AttackCommand {
    suspend fun execute(instance: RogueInstance, player: RoguePlayer)

    // interfaces for modifiers to change.
    interface Damaging {
        var damage: Float
    }
    interface Ranged {
        var range: Double
    }
    interface Bouncing {
        var bounces: Int
    }
    interface Moving {
        var speed: Double
    }

    companion object {
        fun closestEntityTo(
            instance: RogueInstance,
            p: Point,
            range: Double,
            current: LivingEntity? = null,
        ) = closestEntityTo(
            instance.getNearbyEntities(p, range).filterIsInstance<LivingEntity>(),
            p,
            current
        )

        fun closestEntityTo(
            nearbyEntities: List<LivingEntity>,
            p: Point,
            current: LivingEntity? = null
        ) : LivingEntity? = nearbyEntities
            .filter { it != current }
            .minByOrNull { it.position.distance(p) }
    }
}