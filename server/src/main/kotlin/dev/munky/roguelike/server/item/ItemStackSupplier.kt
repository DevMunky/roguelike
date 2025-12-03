package dev.munky.roguelike.server.item

import net.minestom.server.item.ItemStack

interface ItemStackSupplier {
    fun buildItemStack(): ItemStack
}