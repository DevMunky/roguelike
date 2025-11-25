package dev.munky.roguelike.server.item

import com.google.errorprone.annotations.Immutable
import dev.munky.roguelike.server.player.RoguelikePlayer
import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponent
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.tag.TagReadable
import org.jetbrains.annotations.UnknownNullability

abstract class Weapon(
    private val modifiers: List<Modifier>
) : RoguelikeItem() {
    data class Modifier(
        val id: Key,
        val modifier: Weapon.() -> Weapon,
    )

    class Builder() {
        var modifiers = ArrayList<Modifier>()

        fun withModifier(modifier: Modifier) {
            modifiers += modifier
        }
    }

    companion object {
    }
}

sealed class Skill : RoguelikeItem()

@Immutable
sealed class RoguelikeItem(stack: ItemStack? = null) : DataComponent.Holder, TagReadable {
    init {
        if (stack != null) require(stack.hasTag(TAG)) { "RoguelikeItem must have the appropriate tag." }
    }

    abstract fun onLeftClick(player: RoguelikePlayer)
    abstract fun onRightClick(player: RoguelikePlayer, target: InteractTarget)

    val stack: ItemStack = stack ?: ItemStack.builder(Material.fromKey("minecraft:barrier")).apply {
        setTag(TAG, this@RoguelikeItem)
    }.build()

    override fun <T : Any?> getTag(tag: Tag<T?>?): @UnknownNullability T? = stack.getTag(tag)
    override fun hasTag(tag: Tag<*>?): Boolean = stack.hasTag(tag)

    override fun <T : Any?> get(component: DataComponent<T?>?, defaultValue: T?): T? = stack.get(component, defaultValue)
    override fun <T : Any> get(component: DataComponent<T>): T? = stack.get(component)
    override fun has(component: DataComponent<*>?): Boolean = stack.has(component)

    sealed interface InteractTarget {
        data class Entity(val entity: net.minestom.server.entity.Entity) : InteractTarget
        data class Block(val block: net.minestom.server.instance.block.Block) : InteractTarget
    }

    companion object {
        /**
         * Used to get the RoguelikeItem from an itemstack in various events.
         */
        val TAG = Tag.Transient<RoguelikeItem>("roguelike:item")
        val EVENT_NODE = EventNode.type("roguelike:weapon", EventFilter.PLAYER)

        fun registerEvents() {
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