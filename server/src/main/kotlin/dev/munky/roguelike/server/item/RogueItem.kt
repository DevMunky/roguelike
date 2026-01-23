package dev.munky.roguelike.server.item

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.serialization.toAdventure
import dev.munky.roguelike.common.serialization.toKNbt
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguePlayer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import net.benwoodworth.knbt.Nbt
import net.benwoodworth.knbt.NbtCompression
import net.benwoodworth.knbt.NbtEncoder
import net.benwoodworth.knbt.NbtVariant
import net.kyori.adventure.nbt.ByteArrayBinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.LongBinaryTag
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
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
import net.minestom.server.item.component.CustomData
import java.util.UUID

@Serializable
sealed interface RogueItem : RenderContext.Element {
    override val key: RenderContext.Key<*> get() = Companion

    val uuid: UUID

    fun onLeftClick(player: RoguePlayer)
    fun onRightClick(player: RoguePlayer, target: InteractTarget)

    fun onEvent(e: ItemEvent) {}

    fun buildItemStack(): ItemStack

    fun createCustomItemStack(): ItemStack {
        val subclass = buildItemStack()
        val data = PROTOBUF_FORMAT.encodeToByteArray(serializer(), this)
        var nbt = subclass.get(DataComponents.CUSTOM_DATA)?.nbt ?: CustomData.EMPTY.nbt
        val rootTag = CompoundBinaryTag.builder()
            .put("uuid_least", LongBinaryTag.longBinaryTag(uuid.leastSignificantBits))
            .put("uuid_most", LongBinaryTag.longBinaryTag(uuid.mostSignificantBits))
            .put("data", ByteArrayBinaryTag.byteArrayBinaryTag(*data))
            .build()
        nbt = nbt.put(CompoundBinaryTag.builder()
            .put("rogue_item", rootTag)
            .build())
        return subclass.with(DataComponents.CUSTOM_DATA, CustomData(nbt))
    }

    sealed interface InteractTarget {
        data class Entity(val entity: net.minestom.server.entity.Entity) : InteractTarget
        data class Block(val block: net.minestom.server.instance.block.Block) : InteractTarget
    }

    companion object : RenderContext.Key<RogueItem> {
        val EVENT_NODE: EventNode<Event> = EventNode.all("${Roguelike.NAMESPACE}:item.events")
        val PLAYER_EVENT_NODE: EventNode<PlayerEvent> = EventNode.type("${Roguelike.NAMESPACE}:weapon.events.player", EventFilter.PLAYER)
        val ITEM_EVENT_NODE: EventNode<ItemEvent> = EventNode.type("${Roguelike.NAMESPACE}:weapon.events.item", EventFilter.ITEM)

        val PROTOBUF_FORMAT = ProtoBuf {}

        fun getRogueItem(item: ItemStack, player: RoguePlayer?, hotbar: Byte?) : RogueItem? {
            val nbt = item.get(DataComponents.CUSTOM_DATA)?.nbt ?: return null
            val rogueData = nbt.getCompound("rogue_item", null) ?: return null

            val cachedItem = when {
                player != null && hotbar != null -> player.hotbar[hotbar.toInt()]
                player != null && hotbar == null -> {
                    val least = rogueData.getLong("uuid_least")
                    val most = rogueData.getLong("uuid_most")
                    var retItem: RogueItem? = null
                    for (item in player.hotbar) {
                        item ?: continue
                        if (item.uuid.leastSignificantBits == least && item.uuid.mostSignificantBits == most) {
                            retItem = item
                            break
                        }
                    }
                    retItem
                }
                else -> null
            }

            val data = rogueData.getByteArray("data", null) ?: return null
            if (cachedItem == null) {
                println("Cache miss for rogue item")
            }
            return cachedItem ?: PROTOBUF_FORMAT.decodeFromByteArray(serializer(), data)
        }

        fun initialize() {
            PLAYER_EVENT_NODE.addListener(PlayerHandAnimationEvent::class.java) {
                if (it.hand != PlayerHand.MAIN) return@addListener
                val player = it.player as? RoguePlayer ?: return@addListener

                val roguelike = getRogueItem(player.itemInMainHand, player, player.heldSlot) ?: return@addListener
                roguelike.onLeftClick(player)
            }
            PLAYER_EVENT_NODE.addListener(PlayerEntityInteractEvent::class.java) {
                val player = it.player as? RoguePlayer ?: return@addListener

                val roguelike = getRogueItem(player.itemInMainHand, player, player.heldSlot) ?: return@addListener
                roguelike.onRightClick(player, InteractTarget.Entity(it.target))
            }
            PLAYER_EVENT_NODE.addListener(PlayerBlockInteractEvent::class.java) {
                val player = it.player as? RoguePlayer ?: return@addListener

                val roguelike = getRogueItem(player.itemInMainHand, player, player.heldSlot) ?: return@addListener
                roguelike.onRightClick(player, InteractTarget.Block(it.block))
            }
            ITEM_EVENT_NODE.addListener(ItemEvent::class.java) {
                val roguelike = getRogueItem(it.itemStack, null, null) ?: return@addListener
                roguelike.onEvent(it)
            }

            EVENT_NODE.addChild(ITEM_EVENT_NODE)
            EVENT_NODE.addChild(PLAYER_EVENT_NODE)
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}