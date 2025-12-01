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
import java.awt.Component

/**
 * State that must persist between weapon iterations
 * or player sessions goes here (serialized data).
 */
@Serializable
sealed interface Modifier {
    val id: Key
    val genre: Genre
    val count: Int

    fun create(weapon: WeaponInstance) : ModifierInstance

    enum class Genre {
        PRIMARILY_APPEND, // first in the modifier list
        PRIMARILY_MODIFY, // second and so on
    }
}

/**
 * Runtime state is stored here
 */
abstract class ModifierInstance : Comparable<ModifierInstance> {
    abstract val modifier: Modifier

    abstract fun decorate(ctx: ItemStack) : ItemStack
    abstract fun attack(ctx: AttackContext)

    override fun compareTo(other: ModifierInstance): Int {
        return modifier.genre.compareTo(other.modifier.genre)
    }

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

@Serializable
data class ModifierFlameBurst(override val count: Int) : Modifier {
    override val id = "modifier.flame".roguelikeKey()
    override val genre = Modifier.Genre.PRIMARILY_APPEND
    override fun create(weapon: WeaponInstance): ModifierInstance = Instance(this)

    class Instance(override val modifier: Modifier) : ModifierInstance() {
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