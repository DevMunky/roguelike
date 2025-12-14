package dev.munky.roguelike.server.enemy.ai.behavior

import dev.munky.roguelike.server.enemy.ai.Ai
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.pathfinding.NavigableEntity
import kotlin.math.pow

@Serializable
@SerialName("chase_target")
object ChaseTarget : AiBehavior {
    override fun <T> priority(context: Ai.Context, entity: T): Double where T : LivingEntity, T : NavigableEntity {
        val target = context[Ai.Context.Key.TARGET] ?: return 0.0
        val distance = entity.position.distanceSquared(target.position)
        val maxDistance = 20.0.pow(2)
        if (distance >= maxDistance) return 0.0
        return distance / maxDistance
    }

    override suspend fun <T> start(context: Ai.Context, entity: T) where T : LivingEntity, T : NavigableEntity {
        val target = context[Ai.Context.Key.TARGET] ?: return
        while (true) {
            withTimeoutOrNull(10000) {
                entity.navigator.setPathTo(target.position, 1.5, 60.0, 200.0) {}
            } ?: return
            delay(200)
        }
    }
}