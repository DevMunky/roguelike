package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.RoguelikeInstance
import dev.munky.roguelike.server.roguelikeKey
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.item.ItemStack

@Serializable
sealed interface Modifier {
    val id: Key
    fun create(weapon: WeaponInstance) : ModifierInstance
}

sealed class ModifierInstance(val modifier: Modifier) {
    abstract fun decorate(ctx: ItemStack) : ItemStack
    abstract fun attack(ctx: AttackContext)

    fun entitiesInFront(instance: RoguelikeInstance, position: Pos, fov: Double, dist: Double) : List<LivingEntity> {
        val look = position.direction()
        val filtered = instance.getNearbyEntities(position, dist).asSequence()
            .filterIsInstance<LivingEntity>()
            .filter {
                val straight = it.position.sub(position).asVec().normalize()
                val dist = straight.sub(look).lengthSquared()
                dist < fov * fov
            }.toList()
        return filtered
    }
}

object ModifierFlameBurst : Modifier {
    override val id = "modifier.flame".roguelikeKey()
    override fun create(weapon: WeaponInstance): ModifierInstance = Instance()

    class Instance : ModifierInstance(ModifierFlameBurst) {
        override fun decorate(ctx: ItemStack): ItemStack {
            val lore = ctx.get(DataComponents.LORE)?.let { ArrayList(it) } ?: arrayListOf()
            lore += "<red>Makes flame".asComponent()
            return ctx.withLore(lore)
        }

        override fun attack(ctx: AttackContext) {
            ctx.actions += entitiesInFront(ctx.instance, ctx.player.position, 1.0, 3.0).map {
                AttackCommand.Ignite(it, 3f)
            }
        }
    }
}