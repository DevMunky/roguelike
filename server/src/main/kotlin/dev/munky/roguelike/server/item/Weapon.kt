package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.instance.RoguelikeInstance
import dev.munky.roguelike.server.item.RoguelikeItem.Companion.TAG
import dev.munky.roguelike.server.player.RoguelikePlayer
import kotlinx.serialization.Serializable
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

@Serializable
data class Weapon(
    val style: CombatStyle,
    val modifiers: Set<Modifier>
) {
    enum class CombatStyle(val itemModel: String) {
        SWORD("roguelike:weapon.sword"),
        SPELL("roguelike:weapon.spell")
    }
}

data class WeaponInstance(val data: Weapon) : RoguelikeItem {
    val modifiers = data.modifiers.map { it.create(this) }

    override fun onRightClick(player: RoguelikePlayer, target: RoguelikeItem.InteractTarget) {}
    override fun onLeftClick(player: RoguelikePlayer) {
        val instance = player.instance as? RoguelikeInstance ?: return
        val ctx = AttackContext(instance, player)
        modifiers.forEach {
            it.attack(ctx)
        }
        ctx.attack()
    }

    override fun buildItem(): ItemStack {
        val base = ItemStack.builder(Material.PAPER)
            .itemModel(data.style.itemModel).build()
        RoguelikeItem.MAP[base] = this
        return modifiers.fold(base) { acc, modifier -> modifier.decorate(acc) }
    }
}