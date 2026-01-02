package dev.munky.roguelike.server.item.attack.command

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.util.ParticleUtil
import kotlinx.coroutines.delay
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle

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
            ParticleUtil.drawParticlesAround(instance, spawnPos,
                BoundingBox(1.0, 1.0, 1.0), particle, amount = 5)
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

            delay(iterationTime)
        }
    }
}