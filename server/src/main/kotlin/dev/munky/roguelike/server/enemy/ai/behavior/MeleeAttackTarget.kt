package dev.munky.roguelike.server.enemy.ai.behavior

import dev.munky.roguelike.server.enemy.ai.Ai
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.pathfinding.NavigableEntity
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import kotlin.math.pow

@Serializable
@SerialName("melee_attack_target")
object MeleeAttackTarget : AiBehavior {
    const val ATTACK_DISTANCE = 2.0

    override fun <T> priority(context: Ai.Context, entity: T): Double where T : LivingEntity, T : NavigableEntity {
        val target = context[Ai.Context.Key.TARGET] ?: return 0.0
        val distance = entity.position.distanceSquared(target.position)
        val maxDistance = ATTACK_DISTANCE.pow(2)
        if (distance >= maxDistance) return 0.0
        return 1 - (distance / maxDistance)
    }

    override suspend fun <T> start(
        context: Ai.Context,
        entity: T
    ) where T : LivingEntity, T : NavigableEntity {
        val target = context[Ai.Context.Key.TARGET] ?: return
        val instance = context[Ai.Context.Key.INSTANCE] ?: return

        while (!target.isDead && !entity.isDead && target.instance == entity.instance) {
            entity.lookAt(target)
            instance.sendGroupedPacket(ParticlePacket(
                Particle.ELECTRIC_SPARK, entity.position, entity.boundingBox.relativeEnd.div(1.5), 0.05f, 6
            ))
            delay(400) // telegraph

            // TODO stat stuff
            entity.swingMainHand()
            target.damage(Damage.fromEntity(entity, 2f))
            instance.sendGroupedPacket(ParticlePacket(
                Particle.CRIT, target.position, entity.boundingBox.relativeEnd.div(2.0), 0.5f, 20
            ))
            delay(590) // end lag
        }
        context.remove(Ai.Context.Key.TARGET)
    }
}