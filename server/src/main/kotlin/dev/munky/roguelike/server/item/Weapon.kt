package dev.munky.roguelike.server.item

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.serialization.UUIDSerializer
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.item.modifier.Modifier
import dev.munky.roguelike.server.item.modifier.ModifierData
import dev.munky.roguelike.server.player.RoguePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.component.DataComponents
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.event.inventory.InventoryItemChangeEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.trait.ItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomData
import net.minestom.server.item.component.UseCooldown
import java.util.UUID

@Serializable
data class Weapon(val data: WeaponData) : RogueItem {
    @Serializable(with = UUIDSerializer::class)
    override val uuid: UUID = UUID.randomUUID()

    val modifiers: Map<String, Modifier> = data.modifiers.entries.associate { it.key to it.value.create() }

    override fun onRightClick(player: RoguePlayer, target: RogueItem.InteractTarget) {}
    override fun onLeftClick(player: RoguePlayer) {
        val instance = player.instance as? RogueInstance ?: return
        val ctx = AttackContext(instance, player)
        modifiers.values.sorted().forEach { mod ->
            repeat(mod.data.count) { mod.attack(ctx) }
        }
        Dispatchers.Default.launch {
            ctx.attack()
        }
    }

    override fun onEvent(e: ItemEvent) {
        when (e) {
            is InventoryPreClickEvent -> {
                e.isCancelled = true
            }
        }
    }

    override fun buildItemStack(): ItemStack {
        val base = ItemStack.builder(Material.PAPER)
            .customName(data.style.itemName.decoration(TextDecoration.ITALIC, false))
            .set(DataComponents.USE_COOLDOWN, UseCooldown(.1f + modifiers.size.toFloat() * 1.5f, "weapon.cooldown"))
            .itemModel(data.style.itemModel).build()
        return modifiers.values.fold(base) { acc, modifier -> modifier.decorateWeapon(acc) }
    }
}

@Serializable
data class WeaponData(
    val style: CombatStyle,
    val modifiers: Map<String, ModifierData>
) {
    fun withModifier(modifier: ModifierData) : WeaponData {
        val id = modifier.id
        val existing = modifiers[id]
        val modifiers = modifiers.toMutableMap()
        if (existing != null) {
            modifiers[id] = existing.copy(count = existing.count + 1)
        } else {
            modifiers[id] = modifier
        }
        return copy(modifiers = modifiers)
    }

    @Serializable
    enum class CombatStyle(val itemModel: String, val itemName: @Contextual Component) {
        SWORD(
            "minecraft:iron_sword",
            "Sword".asComponent()
        ),
        SPELL(
            "${Roguelike.NAMESPACE}:weapon/spell",
            "Spell".asComponent()
        )
    }
}