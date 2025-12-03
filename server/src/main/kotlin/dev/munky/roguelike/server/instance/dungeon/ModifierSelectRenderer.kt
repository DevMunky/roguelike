package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.interact.HoverableInteractableCreature
import dev.munky.roguelike.server.item.modifier.ModifierData
import dev.munky.roguelike.server.player.RoguePlayer
import net.kyori.adventure.text.Component
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ModifierSelectRenderer : Renderer {
    data object ModifierSelection : RenderContext.Key<Collection<ModifierData>>
    data object Width : RenderContext.Key<Double>
    data object Radius : RenderContext.Key<Double>

    override suspend fun RenderContext.render() {
        // TODO ensure per player
        val modifiers = require(ModifierSelection)
        val instance = require(RogueInstance)
        val origin = require(RenderKey.Position)
        val width = require(Width)
        val radius = require(Radius)
        val yawRad = -origin.yaw * (PI / 180.0)

        val entities = modifiers.mapIndexed { i, it ->
            ModifierSelect(it, i / modifiers.size.toDouble()) { dispose() }
        }

        val halfPi = PI / 2.0

        val n = modifiers.size
        repeat(n) { i ->
            val theta = if (n == 1) {
                halfPi
            } else {
                val t = i / (n - 1).toDouble()
                (halfPi - width / 2.0) + t * width
            }

            val dx = radius * cos(theta)
            val dy = radius * sin(theta)
            val dz = 0.0

            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)

            // AI rotated this
            val rx = dx * cosYaw + dz * sinYaw
            val rz = -dx * sinYaw + dz * cosYaw

            val x = origin.x + rx
            val y = origin.y + dy
            val z = origin.z + rz
            entities[i].setInstance(instance, Pos(x, y, z).withYaw(origin.yaw))
        }

        onDispose {
            entities.forEach { it.remove() }
        }
    }

    class Container : HoverableInteractableCreature(EntityType.INTERACTION) {
        override fun onInteract(player: RoguePlayer) {

        }
    }

    class ModifierSelect(val modifier: ModifierData, val oscOff: Double, val onSelect: ()->Unit): HoverableInteractableCreature(EntityType.INTERACTION) {
        val height = 1.0
        val width = 1.0

        val itemOffset = -height / 2.0
        val nameOffset = .0
        val descriptionOffset = -height * 1.4

        init {
            editEntityMeta(InteractionMeta::class.java) {
                it.height = height.toFloat()
                it.width = width.toFloat()
            }
            setBoundingBox(width, height, width)
        }

        val item = Entity(EntityType.ITEM_DISPLAY).apply {
            editEntityMeta(ItemDisplayMeta::class.java) {
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
                it.itemStack = modifier.buildItemStack()
                it.translation = Vec(0.0, itemOffset, 0.0)
                it.scale = Vec(0.8)
                it.transformationInterpolationDuration = 1
            }
        }

        val name = Entity(EntityType.TEXT_DISPLAY).apply {
            editEntityMeta(TextDisplayMeta::class.java) {
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
                it.text = (item.entityMeta as ItemDisplayMeta).itemStack.get(DataComponents.CUSTOM_NAME)
                it.translation = Vec(0.0, nameOffset, 0.0)
                it.transformationInterpolationDuration = 1
            }
        }

        val description = Entity(EntityType.TEXT_DISPLAY).apply {
            editEntityMeta(TextDisplayMeta::class.java) {
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
                var desc = Component.empty()
                for ((i, l) in modifier.description.withIndex()) {
                    if (i != 0) desc = desc.appendNewline()
                    desc = desc.append(l.asComponent())
                }
                it.text = desc
                it.translation = Vec(0.0, descriptionOffset - (modifier.description.size * 0.2), 0.0)
            }
            isAutoViewable = false
        }

        override fun spawn() {
            item.setInstance(instance, position)
            description.setInstance(instance, position)
            name.setInstance(instance, position)
            addPassenger(item)
            addPassenger(description)
            addPassenger(name)
        }

        override fun despawn() {
            item.remove()
            description.remove()
            name.remove()
        }

        override fun tick(time: Long) {
            // Some day i will find out why the oscillation is not smooth
            oscillate(time, 3000L, .333)
            super.tick(time)
        }

        fun oscillate(time: Long, rateMillis: Long, delta: Double) {
            val dt = (time % rateMillis) / rateMillis.toDouble()
            var oscillation = sin(oscOff + dt * PI * 2) * 0.5 * delta
            oscillation -= oscillation / 2.0
            item.editEntityMeta(AbstractDisplayMeta::class.java) {
                it.transformationInterpolationStartDelta = 0
                it.translation = it.translation.withY(itemOffset + oscillation)
            }
            name.editEntityMeta(AbstractDisplayMeta::class.java) {
                it.transformationInterpolationStartDelta = 0
                it.translation = it.translation.withY(nameOffset + oscillation)
            }
        }

        override fun onInteract(player: RoguePlayer) {
            player.weaponData = player.weaponData.withModifier(modifier)
            onSelect()
        }

        override fun onHoverStart(player: RoguePlayer) {
            description.addViewer(player)
        }

        override fun onHoverEnd(player: RoguePlayer) {
            description.removeViewer(player)
        }
    }
}