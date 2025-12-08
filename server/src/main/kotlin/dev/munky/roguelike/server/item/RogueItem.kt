package dev.munky.roguelike.server.item

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.trait.ItemEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.item.ItemStack
import java.util.UUID
import java.util.WeakHashMap

sealed interface RogueItem : ItemStackSupplier, RenderContext.Element {
    override val key: RenderContext.Key<*> get() = Companion

    val uuid: UUID

    fun onLeftClick(player: RoguePlayer)
    fun onRightClick(player: RoguePlayer, target: InteractTarget)

    fun onEvent(e: ItemEvent) {}

    sealed interface InteractTarget {
        data class Entity(val entity: net.minestom.server.entity.Entity) : InteractTarget
        data class Block(val block: net.minestom.server.instance.block.Block) : InteractTarget
    }

    companion object : RenderContext.Key<RogueItem> {
        val MAP = WeakHashMap<ItemStack, RogueItem>()

        val EVENT_NODE: EventNode<Event> = EventNode.all("${Roguelike.NAMESPACE}:item.events")
        val PLAYER_EVENT_NODE: EventNode<PlayerEvent> = EventNode.type("${Roguelike.NAMESPACE}:weapon.events.player", EventFilter.PLAYER)
        val ITEM_EVENT_NODE: EventNode<ItemEvent> = EventNode.type("${Roguelike.NAMESPACE}:weapon.events.item", EventFilter.ITEM)

        fun initialize() {
            PLAYER_EVENT_NODE.addListener(PlayerHandAnimationEvent::class.java) {
                if (it.hand != PlayerHand.MAIN) return@addListener
                val player = it.player as? RoguePlayer ?: return@addListener
                val roguelike = MAP[it.player.itemInMainHand] ?: return@addListener // not roguelike item
                roguelike.onLeftClick(player)
            }
            PLAYER_EVENT_NODE.addListener(PlayerEntityInteractEvent::class.java) {
                val player = it.player as? RoguePlayer ?: return@addListener
                val roguelike = MAP[it.player.itemInMainHand] ?: return@addListener // not roguelike item
                roguelike.onRightClick(player, InteractTarget.Entity(it.target))
            }
            PLAYER_EVENT_NODE.addListener(PlayerBlockInteractEvent::class.java) {
                val player = it.player as? RoguePlayer ?: return@addListener
                val roguelike = MAP[it.player.itemInMainHand] ?: return@addListener // not roguelike item
                roguelike.onRightClick(player, InteractTarget.Block(it.block))
            }
            ITEM_EVENT_NODE.addListener(ItemEvent::class.java) {
                val roguelike = MAP[it.itemStack] ?: return@addListener // not roguelike item
                roguelike.onEvent(it)
            }

            EVENT_NODE.addChild(ITEM_EVENT_NODE)
            EVENT_NODE.addChild(PLAYER_EVENT_NODE)
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}