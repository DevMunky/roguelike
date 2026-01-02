package dev.munky.roguelike.server.item.attack.command

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.sendGroupedPacketBundle
import dev.munky.roguelike.server.util.ParticleUtil
import kotlinx.coroutines.delay
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.particle.Particle
import kotlin.collections.filterIsInstance
import kotlin.random.Random

data class BouncingDamage(
    override var damage: Float,
    override var bounces: Int,
    override var range: Double,
    val particle: Particle,
    val visualEffect: VisualEffect = VisualEffect.STRAIGHT,
) : AttackCommand, AttackCommand.Damaging, AttackCommand.Ranged, AttackCommand.Bouncing {
    override suspend fun execute(instance: RogueInstance, player: RoguePlayer) {
        val nearbyEntities = instance.getNearbyEntities(player.position, range)
            .filterIsInstance<LivingEntity>().filter { it != player }
        var current = AttackCommand.closestEntityTo(nearbyEntities, player.position, null) ?: return // nothing to hit

        // can bounce between the same three entities, but not two.
        repeat(bounces) {
            current.damage(Damage(DamageType.MAGIC, player, null, null, damage))
            ParticleUtil.drawParticlesAround(
                instance,
                current,
                particle,
                amount = 15,
                expansion = 1.2,
            )

            val next = AttackCommand.closestEntityTo(nearbyEntities, player.position, current) ?: return

            val midpoint1 = midpointOf(current.boundingBox).add(current.position)
            val midpoint2 = midpointOf(next.boundingBox).add(next.position)

            val drawnPackets = when (visualEffect) {
                VisualEffect.LIGHTNING -> ParticleUtil.drawLightningArc(midpoint1, midpoint2, particle, 10, 0.2, Random.Default)
                VisualEffect.STRAIGHT -> ParticleUtil.drawLine(midpoint1, midpoint2, particle, 10)
            }

            instance.sendGroupedPacketBundle(drawnPackets)

            current = next
            delay(50)
        }
    }

    private fun midpointOf(bb: BoundingBox) : Vec {
        val h = bb.height() / 2
        val w = bb.width() / 2
        return Vec(bb.minX() + w, bb.minY() + h, bb.minZ() + w)
    }

    enum class VisualEffect {
        STRAIGHT, LIGHTNING
    }
}

