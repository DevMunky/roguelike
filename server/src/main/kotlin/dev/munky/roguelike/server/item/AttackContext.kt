package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle

class AttackContext(
    val instance: RogueInstance,
    val player: RoguePlayer,
) {
    val actions = ArrayList<AttackCommand>()

    fun attack() {
        for (action in actions) action.execute(instance, player)
    }
}

sealed interface AttackCommand {
    fun execute(instance: RogueInstance, player: RoguePlayer)

    data class Ignite(val target: LivingEntity, val damage: Float) : AttackCommand {
        override fun execute(instance: RogueInstance, player: RoguePlayer) = when (target.entityType) {
            EntityType.ITEM_DISPLAY, EntityType.TEXT_DISPLAY,
            EntityType.BLOCK_DISPLAY, EntityType.INTERACTION -> {}
            else -> {
                target.damage(Damage(DamageType.ON_FIRE, player, null, null, damage))
                spawnParticlesAround(instance, target, Particle.FLAME, 10)
            }
        }
    }

    companion object {
        fun spawnParticlesAround(instance: RogueInstance, entity: LivingEntity, particle: Particle, amount: Int, speed: Float = 0f) {
            val xz = entity.boundingBox.width() * 1.05
            val y = entity.boundingBox.height() * 1.05
            val particles = ArrayList<ParticlePacket>()
            repeat(amount) {
                particles += ParticlePacket(particle, entity.position, Vec(xz, y, xz), speed, 1)
            }
            for (player in instance.players) {
                player.sendPackets(particles as List<ParticlePacket>)
            }
        }
    }
}