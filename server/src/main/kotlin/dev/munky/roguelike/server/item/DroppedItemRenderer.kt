package dev.munky.roguelike.server.item

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandle
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.interact.HoverableInteractableCreature
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.random.Random

object DroppedItemRenderer : Renderer {
    override suspend fun RenderContext.render() {
        val player = require(RoguePlayer)
        val instance = require(RogueInstance)
        val item = require(RogueItem)
        val dropPosition = require(RenderKey.Position)

        val e = DroppedItem(handle(), player, item)
        e.setInstance(instance, dropPosition.withView(0f, 0f))

        onDispose {
            e.remove()
        }
    }
}

/**
 * Note: uses setNoGravity(false) to put the interaction entity on the ground.
 * In the future I would like more control regarding this, as well as rotations while falling.
 */
class DroppedItem(val handle: RenderHandle, val player: RoguePlayer, val item: RogueItem) : HoverableInteractableCreature(EntityType.INTERACTION) {
    init {
        isAutoViewable = false
        setNoGravity(false)
        editEntityMeta(InteractionMeta::class.java) {
            it.height = 0.15f
            it.width = 0.75f
        }
        setBoundingBox(0.75, .15, 0.75)
        collidesWithEntities = false
    }

    val itemDisplay = Entity(EntityType.ITEM_DISPLAY).apply {
        editEntityMeta(ItemDisplayMeta::class.java) {
            it.itemStack = item.createCustomItemStack()
            it.posRotInterpolationDuration = 1
        }
        isAutoViewable = false
        collidesWithEntities = false
    }

    override fun onInteract(player: RoguePlayer) {
        player.inventory.addItemStack(item.createCustomItemStack())
        handle.dispose()
    }

    override fun spawn() {
        itemDisplay.setInstance(instance, position)
        addViewer(player)
        itemDisplay.addViewer(player)
        addPassenger(itemDisplay)
        val leftRot = Quaternionf()
            .rotateY(Random.nextFloat() * 2f * PI.toFloat())
            .rotateX(PI.toFloat() / 2f)
        itemDisplay.editEntityMeta(ItemDisplayMeta::class.java) {
            it.leftRotation = floatArrayOf(leftRot.x, leftRot.y, leftRot.z, leftRot.w)
            it.translation = Vec(.0, -.1, .0)
        }
    }

    override fun despawn() {
        itemDisplay.remove()
    }

    override fun onHoverStart(player: RoguePlayer) {
        itemDisplay.isGlowing = true
    }

    override fun onHoverEnd(player: RoguePlayer) {
        itemDisplay.isGlowing = false
    }
}