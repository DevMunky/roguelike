package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.instance.RoguelikeInstance
import dev.munky.roguelike.server.player.RoguelikePlayer
import kotlinx.serialization.Serializable
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.UseCooldown
import net.minestom.server.tag.Tag
import java.util.UUID

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
    override val uuid: UUID = UUID.randomUUID()

    val modifiers = data.modifiers.map { it.create(this) }

    override fun onRightClick(player: RoguelikePlayer, target: RoguelikeItem.InteractTarget) {}
    override fun onLeftClick(player: RoguelikePlayer) {
        val instance = player.instance as? RoguelikeInstance ?: return
        val ctx = AttackContext(instance, player)
        modifiers.sorted().forEach { mod ->
            repeat(mod.modifier.count) {
                mod.attack(ctx)
            }
        }
        ctx.attack()
    }

    override fun buildItem(): ItemStack {
        val base = ItemStack.builder(Material.PAPER)
            .set(DataComponents.USE_COOLDOWN, UseCooldown(modifiers.size.toFloat() * 1.5f, "weapon.cooldown"))
            .itemModel(data.style.itemModel).build()
        val final = modifiers.fold(base) { acc, modifier -> modifier.decorate(acc) }
        RoguelikeItem.MAP[final] = this
        return final
    }
}