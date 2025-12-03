package dev.munky.roguelike.server.item.modifier

import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.item.AttackCommand
import dev.munky.roguelike.server.item.AttackContext
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack

data class FlameBurstModifier(override val data: ModifierData) : Modifier {
    override fun decorateWeapon(ctx: ItemStack): ItemStack {
        val lore = ctx.get(DataComponents.LORE)?.let { ArrayList(it) } ?: arrayListOf()
        lore += "<white>(${data.id}) <red>Makes flame".asComponent().decoration(TextDecoration.ITALIC, false)
        return ctx.withLore(lore)
    }

    override fun attack(ctx: AttackContext) {
        ctx.actions += entitiesInFront(ctx.instance, ctx.player.position, 1.0, 3.0).map {
            AttackCommand.Ignite(it, 3f)
        }
    }
}