package dev.munky.roguelike.server.item.attack.command

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.util.ParticleUtil
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.particle.Particle

data class InstantBurn(
    override var damage: Float,
    val target: LivingEntity,
) : AttackCommand, AttackCommand.Damaging {
    override suspend fun execute(instance: RogueInstance, player: RoguePlayer) = when (target.entityType) {
        EntityType.ITEM_DISPLAY, EntityType.TEXT_DISPLAY,
        EntityType.BLOCK_DISPLAY, EntityType.INTERACTION -> {}
        else -> {
            target.damage(Damage(DamageType.ON_FIRE, player, null, null, damage))
            ParticleUtil.drawParticlesAround(instance, target, Particle.FLAME, amount = 10)
        }
    }
}