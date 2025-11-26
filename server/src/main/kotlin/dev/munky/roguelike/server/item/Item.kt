package dev.munky.roguelike.server.item

import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponent
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.tag.Tag
import net.minestom.server.tag.TagReadable
import org.jetbrains.annotations.UnknownNullability

sealed class RoguelikeItem(stack: ItemStack? = null) : DataComponent.Holder, TagReadable {
    init {
        if (stack != null) modifyItem { stack }
    }

    var itemStack: ItemStack = ItemStack.AIR.withTag(TAG, this)
        private set(value) {
            if (!value.hasTag(TAG)) error("Modified ItemStack does not have roguelike item tag.")
            field = value
        }

    abstract fun onLeftClick(player: RoguelikePlayer)
    abstract fun onRightClick(player: RoguelikePlayer, target: InteractTarget)

    protected fun modifyItem(f: (ItemStack) -> ItemStack) {
        itemStack = f(itemStack)
    }

    override fun <T : Any> getTag(tag: Tag<T>): @UnknownNullability T? = itemStack.getTag(tag)
    override fun hasTag(tag: Tag<*>?): Boolean = itemStack.hasTag(tag)

    override fun <T : Any> get(component: DataComponent<T>, defaultValue: T): T? = itemStack.get(component, defaultValue)
    override fun <T : Any> get(component: DataComponent<T>): T? = itemStack.get(component)
    override fun has(component: DataComponent<*>?): Boolean = itemStack.has(component)

    sealed interface InteractTarget {
        data class Entity(val entity: net.minestom.server.entity.Entity) : InteractTarget
        data class Block(val block: net.minestom.server.instance.block.Block) : InteractTarget
    }

    companion object {
        /**
         * Used to get the RoguelikeItem from an itemstack in various events.
         */
        val TAG = Tag.Transient<RoguelikeItem>("${Roguelike.NAMESPACE}:item")
        val EVENT_NODE = EventNode.type("${Roguelike.NAMESPACE}:weapon", EventFilter.PLAYER)

        fun initialize() {
            EVENT_NODE.addListener(PlayerHandAnimationEvent::class.java) {
                if (it.hand != PlayerHand.MAIN) return@addListener
                val player = it.player as? RoguelikePlayer ?: return@addListener
                val roguelike = it.player.itemInMainHand.getTag(TAG) ?: return@addListener // not roguelike item
                roguelike.onLeftClick(player)
            }
            EVENT_NODE.addListener(PlayerEntityInteractEvent::class.java) {
                val player = it.player as? RoguelikePlayer ?: return@addListener
                val roguelike = it.player.itemInMainHand.getTag(TAG) ?: return@addListener // not roguelike item
                roguelike.onRightClick(player, InteractTarget.Entity(it.target))
            }
            EVENT_NODE.addListener(PlayerBlockInteractEvent::class.java) {
                val player = it.player as? RoguelikePlayer ?: return@addListener
                val roguelike = it.player.itemInMainHand.getTag(TAG) ?: return@addListener // not roguelike item
                roguelike.onRightClick(player, InteractTarget.Block(it.block))
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}