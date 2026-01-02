package dev.munky.roguelike.server.item.modifier

import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.item.AttackContext
import dev.munky.roguelike.server.item.attack.command.InstantBurn
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack

data class FlameBurst(override val data: ModifierData) : Modifier {
    override fun decorateWeapon(ctx: ItemStack): ItemStack {
        val lore = ctx.get(DataComponents.LORE)?.let { ArrayList(it) } ?: arrayListOf()
        lore += "<red>Makes flame".asComponent()
        return ctx.withLore(lore)
    }

    override fun attack(ctx: AttackContext) {
        ctx.actions += entitiesInFront(ctx.instance, ctx.player.position, 1.0, 3.0).map {
            InstantBurn(it, 3f)
        }
    }
}