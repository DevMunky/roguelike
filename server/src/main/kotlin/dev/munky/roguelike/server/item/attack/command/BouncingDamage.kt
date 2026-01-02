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
import net.minestom.server.network.packet.server.play.ParticlePacket
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

data class HomingProjectile(
    override var damage: Float,
    override var speed: Double,
    val spawnOffset: Vec,
    val particle: Particle,
) : AttackCommand, AttackCommand.Moving, AttackCommand.Damaging {
    override suspend fun execute(instance: RogueInstance, player: RoguePlayer) {
        // Find a target within a cone or radius
        val target = AttackCommand.closestEntityTo(
            instance,
            player.position,
            15.0,
        ) ?: return // no target

        val spawnPos = player.position.add(spawnOffset)

        // hang in the air
        repeat(5) {
            ParticleUtil.drawParticlesAround(instance, spawnPos, BoundingBox(1.0, 1.0, 1.0), particle, amount = 5)
            delay(50)
        }

        var currentPos = spawnPos
        val iterations = 20
        val iterationTime = 25L // millis between iterations
        val deltaSpeed = speed * (1/1000.0) * iterationTime
        // b/s * s/ms = b/ms -> b/ms * ms/iteration = blocks/iteration

        repeat(iterations) {
            val dir = target.position.add(0.0, 1.0, 0.0)
                .sub(currentPos).asVec()
                .normalize().mul(deltaSpeed)
            currentPos = currentPos.add(dir)

            // Visual trail
            instance.sendGroupedPacket(
                ParticlePacket(particle, currentPos, Vec.ZERO, 0f, 1)
            )

            if (currentPos.distance(target.position) < 0.3) {
                ParticleUtil.drawParticlesAround(instance, target, particle, amount = 10)
                target.damage(Damage(DamageType.MAGIC, player, null, null, damage))
                return@execute
            }

            delay(iterationTime) // 1 tick movement
        }
    }
}