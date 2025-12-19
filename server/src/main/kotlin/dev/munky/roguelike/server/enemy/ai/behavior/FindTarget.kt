package dev.munky.roguelike.server.enemy.ai.behavior

import dev.munky.roguelike.server.enemy.ai.Ai
import dev.munky.roguelike.server.player.RoguePlayer
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.pathfinding.NavigableEntity

@Serializable
@SerialName("find_target")
object FindTarget : AiBehavior {
    override fun <T> priority(context: Ai<T>.Context, entity: T): Double where T : LivingEntity, T : NavigableEntity = when {
        Ai.ContextKey.TARGET in context -> 0.0
        Ai.ContextKey.INSTANCE !in context -> 0.0
        else -> 0.01
    }

    override suspend fun <T> start(context: Ai<T>.Context, entity: T) where T : LivingEntity, T : NavigableEntity {
        val instance = context[Ai.ContextKey.INSTANCE] ?: return
        var target: LivingEntity? = null
        while (target == null) {
            target = context[Ai.ContextKey.TARGET] ?: instance.getNearbyEntities(entity.position, 20.0)
                .filterIsInstance<LivingEntity>()
                .filter { it != entity && !it.isDead && if (it is RoguePlayer) !it.isDebug else true}
                .minByOrNull { it.position.distanceSquared(entity.position) }
            delay(200)
        }
        context[Ai.ContextKey.TARGET] = target
    }
}