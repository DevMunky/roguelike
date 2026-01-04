package dev.munky.roguelike.server.enemy.ai.behavior

import dev.munky.roguelike.server.enemy.ai.Ai
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.raycast.Ray
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.pathfinding.NavigableEntity

@Serializable
@SerialName("find_target")
object FindTarget : AiBehavior {
    const val MAX_DISTANCE = 20.0

    override fun <T> priority(context: Ai.Context, entity: T): Double where T : LivingEntity, T : NavigableEntity = when {
        Ai.Context.Key.TARGET in context -> 0.0
        Ai.Context.Key.INSTANCE !in context -> 0.0
        else -> 0.01
    }

    override suspend fun <T> start(context: Ai.Context, entity: T) where T : LivingEntity, T : NavigableEntity {
        val instance = context[Ai.Context.Key.INSTANCE] ?: return
        var target: LivingEntity? = null
        while (target == null) {
            target = context[Ai.Context.Key.TARGET] ?: instance.getNearbyEntities(entity.position, MAX_DISTANCE)
                .filterIsInstance<LivingEntity>()
                .filter { it != entity && !it.isDead && if (it is RoguePlayer) !it.isDebug else true}
                .filter {
                    val dir = it.position.sub(entity.position).asVec().normalize()
                    val blockRay = Ray(entity.position, dir, MAX_DISTANCE)
                    val blocks = blockRay.findBlocks(instance)
                    // filters out targets not occluded by any blocks between their feet
                    blocks.nextClosest() == null
                }
                .minByOrNull { it.position.distanceSquared(entity.position) }
            delay(200)
        }
        context[Ai.Context.Key.TARGET] = target
    }
}