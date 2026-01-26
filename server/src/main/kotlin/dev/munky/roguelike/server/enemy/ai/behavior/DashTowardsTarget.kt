package dev.munky.roguelike.server.enemy.ai.behavior

import dev.munky.roguelike.server.enemy.ai.Ai
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.pathfinding.NavigableEntity

@Serializable
@SerialName("dash_towards_target")
object DashTowardsTarget : AiBehavior {
    const val DASH_COOLDOWN_VALUE = 10.0
    val DASH_COOLDOWN = Ai.Context.Key<Double>("dash_cooldown")
    const val MINIMUM_DISTANCE_FROM_DASH = 20.0
    const val DASH_DISTANCE = 4.0

    override fun <T> priority(context: Ai.Context, entity: T): Double where T : LivingEntity, T : NavigableEntity {
        if (!context.contains(Ai.Context.Key.TARGET)) return 0.0
        val cd = context[DASH_COOLDOWN] ?: return 0.0
        // possibly use MINIMUM_DISTANCE_FROM_DASH to dash more often in a certain radius around the target
        return (cd - 1).coerceAtLeast(0.0) / DASH_COOLDOWN_VALUE
    }

    override suspend fun <T> start(context: Ai.Context, entity: T) where T : LivingEntity, T : NavigableEntity {
        val target = context[Ai.Context.Key.TARGET] ?: return
        val dir = target.position.sub(entity.position).asVec().normalize()
        val dash = dir.mul(DASH_DISTANCE)
        // apply velocity in the direction of the target
        entity.velocity = entity.velocity.add(dash)
    }
}