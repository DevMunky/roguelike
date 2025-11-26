package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.instance.RoguelikeInstance
import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle

class AttackContext(
    val instance: RoguelikeInstance,
    val player: RoguelikePlayer,
) {
    val actions = mutableListOf<AttackCommand>()

    fun attack() {
        for (action in actions) action.execute(instance, player)
    }
}

sealed interface AttackCommand {
    fun execute(instance: RoguelikeInstance, player: RoguelikePlayer)

    fun spawnParticlesAround(instance: RoguelikeInstance, entity: LivingEntity, particle: Particle, amount: Int, speed: Float = 0f) {
        val xz = entity.boundingBox.width() * 1.2
        val y = entity.boundingBox.height() * 1.2
        val particles = ArrayList<ParticlePacket>()
        repeat(amount) {
            particles += ParticlePacket(particle, entity.position, Vec(xz, y, xz), speed, 1)
        }
        for (player in instance.players) {
            player.sendPackets(particles as List<ParticlePacket>)
        }
    }

    data class Ignite(val target: LivingEntity, val damage: Float) : AttackCommand {
        override fun execute(instance: RoguelikeInstance, player: RoguelikePlayer) {
            spawnParticlesAround(instance, target, Particle.FLAME, 20)
            target.damage(Damage(DamageType.ON_FIRE, player, null, null, damage))
        }
    }
}