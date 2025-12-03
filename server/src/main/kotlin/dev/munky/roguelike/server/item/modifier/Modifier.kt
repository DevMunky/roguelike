package dev.munky.roguelike.server.item.modifier

import dev.munky.roguelike.common.serialization.KClassSerializer
import dev.munky.roguelike.server.MiniMessageSerializer
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.item.AttackContext
import dev.munky.roguelike.server.item.ItemStackSupplier
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.LivingEntity
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.reflect.KClass

/**
 * Runtime state is stored here.
 *
 * Sorted base on enum ordinal (lower is first)
 */
interface Modifier : Comparable<Modifier> {
    val data: ModifierData

    fun decorateWeapon(ctx: ItemStack) : ItemStack
    fun attack(ctx: AttackContext)

    override fun compareTo(other: Modifier): Int {
        return data.genre.compareTo(other.data.genre)
    }

    fun entitiesInFront(instance: RogueInstance, position: Pos, fov: Double, dist: Double) : List<LivingEntity> {
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

/**
 * State that must persist between weapon iterations
 * or player sessions goes here (serialized data).
 */
@Serializable
data class ModifierData(
    val id: String,
    val genre: Genre,
    val count: Int,
    /**
     * Description of the modifier.
     */
    val description: List<@Serializable(with = MiniMessageSerializer::class) Component>,
    val modifierClass: @Serializable(with = KClassSerializer::class) KClass<out Modifier>
) : ItemStackSupplier {
    fun create() : Modifier = modifierClass.java.getDeclaredConstructor(ModifierData::class.java).newInstance(this)

    override fun buildItemStack(): ItemStack {
        return ItemStack.builder(Material.PAPER)
            .customName(Component.text(id))
            .itemModel("${Roguelike.NAMESPACE}:$id")
            .lore(description)
            .build()
    }

    enum class Genre {
        PRIMARILY_APPEND, // first in the modifier list
        PRIMARILY_MODIFY, // second and so on
    }
}