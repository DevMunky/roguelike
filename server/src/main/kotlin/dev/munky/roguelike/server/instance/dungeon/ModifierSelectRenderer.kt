package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandle
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.Roguelike
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
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ModifierSelectRenderer : Renderer {
    data object ModifierSelection : RenderContext.Key<Collection<ModifierData>>
    data object Width : RenderContext.Key<Double>
    data object Radius : RenderContext.Key<Double>
    data object ShowModifiers : RenderContext.Key<Unit>
    data object SelectedModifier : RenderContext.Key<ModifierData>

    override suspend fun RenderContext.render() {
        val player = require(RoguePlayer)
        val instance = require(RogueInstance)
        val modifiers = require(ModifierSelection)
        val origin = require(RenderKey.Position).withPitch(0f)
        val width = require(Width)
        val radius = require(Radius)
        val yawRad = -origin.yaw * (PI / 180.0)

        val halfPi = PI / 2.0
        val n = modifiers.size
        val offsets = Array(n) { i ->
            // modifiers start directly above the container
            val theta = if (n == 1) {
                halfPi
            } else {
                val t = i / (n - 1).toDouble()
                (halfPi - width / 2.0) + t * width
            }

            // cylindrical coordinates ; no yaw
            val dx = radius * cos(theta)
            val dy = radius * sin(theta)
            val dz = 0.0

            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)

            // factor in yaw ; spherical coordinates
            val rx = dx * cosYaw + dz * sinYaw
            val rz = -dx * sinYaw + dz * cosYaw

            Vec(rx, dy, rz)
        }

        val entities = modifiers.mapIndexed { i, it ->
            ModifierSelect(handle(), player, it, i / modifiers.size.toDouble(), offsets[i], 50)
        }

        val container = Container(handle(), player)
        container.setInstance(instance, origin)

        watchAndRequire(ShowModifiers) {
            repeat(n) { i ->
                entities[i].setInstance(instance, origin.add(offsets[i]))
            }
        }

        watchAndRequire(SelectedModifier) {
            player.character = player.character.withWeapon(player.character.weapon.data.withModifier(it))
            player.refreshLoadout()
            dispose()
        }

        onDispose {
            container.remove()
            entities.forEach { it.remove() }
        }
    }

    class Container(val handle: RenderHandle, val player: RoguePlayer) :
        HoverableInteractableCreature(EntityType.INTERACTION) {
        init {
            isAutoViewable = false
            editEntityMeta(InteractionMeta::class.java) {
                it.height = 1f
                it.width = 1f
            }
            setBoundingBox(1.0, 1.0, 1.0)
        }

        val display = Entity(EntityType.ITEM_DISPLAY).apply {
            editEntityMeta(ItemDisplayMeta::class.java) {
                it.itemStack = ItemStack.of(Material.PAPER)
                    .with(DataComponents.ITEM_MODEL, "${Roguelike.NAMESPACE}:modifier_container")
            }
            isAutoViewable = false
        }

        override fun spawn() {
            addViewer(player)
            display.setInstance(instance, position)
            display.addViewer(player)

            addPassenger(display)
        }

        override fun despawn() {
            display.remove()
        }

        override fun onInteract(player: RoguePlayer) {
            // visual / audio feedback needed
            handle.context!![ShowModifiers] = Unit
        }
    }

    class ModifierSelect(
        val handle: RenderHandle,
        val player: RoguePlayer,
        val modifier: ModifierData,
        val oscOff: Double,
        val offset: Vec,
        /**
         * Ticks
         */
        val spawnInterpolationDuration: Int
    ) : HoverableInteractableCreature(EntityType.INTERACTION) {
        val height = 1.0
        val width = 1.0

        val itemOffset = -height / 2.0
        val nameOffset = .0
        val descriptionOffset = -height * 1.4
        var doneMoving = true

        init {
            editEntityMeta(InteractionMeta::class.java) {
                it.height = height.toFloat()
                it.width = width.toFloat()
            }
            setBoundingBox(width, height, width)
        }

        val movement = Entity(EntityType.ITEM_DISPLAY).apply {
            editEntityMeta(ItemDisplayMeta::class.java) {
                it.transformationInterpolationDuration = spawnInterpolationDuration - 1
            }
            setNoGravity(true)
            isAutoViewable = false
        }

        val item = Entity(EntityType.ITEM_DISPLAY).apply {
            editEntityMeta(ItemDisplayMeta::class.java) {
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
                it.itemStack = modifier.buildItemStack()
                it.scale = Vec(0.8)
                it.transformationInterpolationDuration = 1
            }
            isAutoViewable = false
        }

        val name = Entity(EntityType.TEXT_DISPLAY).apply {
            editEntityMeta(TextDisplayMeta::class.java) {
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
                it.text =
                    (item.entityMeta as ItemDisplayMeta).itemStack.get(DataComponents.CUSTOM_NAME)
                it.translation = Vec(0.0, nameOffset, 0.0)
                it.transformationInterpolationDuration = 1
            }
            isAutoViewable = false
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
                it.translation =
                    Vec(0.0, descriptionOffset - (modifier.description.size * 0.2), 0.0)
            }
            isAutoViewable = false
        }

        override fun spawn() {
            isAutoViewable = false

            movement.editEntityMeta(ItemDisplayMeta::class.java) {
                it.translation = offset.neg()
            }

            movement.setInstance(instance, position)
            item.setInstance(instance, position)
            description.setInstance(instance, position)
            name.setInstance(instance, position)

            movement.addViewer(player)
            item.addViewer(player)
            name.addViewer(player)
            addViewer(player)

            addPassenger(item)
            addPassenger(description)
            addPassenger(name)

            movement.addPassenger(this)
        }

        override fun despawn() {
            item.remove()
            description.remove()
            name.remove()
            movement.remove()
        }

        override fun tick(time: Long) {
            if (doneMoving) {
                // Some day I will find out why the oscillation is not smooth
                oscillate(time, 3000L, .333)
            }
            super.tick(time)
        }

        fun oscillate(time: Long, rateMillis: Long, delta: Double) {
            val dt = (time % rateMillis) / rateMillis.toDouble()
            var oscillation = sin((oscOff * PI) + dt * PI * 2) * 0.5 * delta
            oscillation -= oscillation / 2.0
            item.editEntityMeta(AbstractDisplayMeta::class.java) {
                it.transformationInterpolationDuration = 1
                it.transformationInterpolationStartDelta = 0
                it.translation = Pos.ZERO.withY(itemOffset + oscillation)
            }
            name.editEntityMeta(AbstractDisplayMeta::class.java) {
                it.transformationInterpolationStartDelta = 0
                it.translation = Pos.ZERO.withY(nameOffset + oscillation)
            }
        }

        override fun onInteract(player: RoguePlayer) {
            handle.context?.set(SelectedModifier, modifier)
        }

        override fun onHoverStart(player: RoguePlayer) {
            description.addViewer(player)
        }

        override fun onHoverEnd(player: RoguePlayer) {
            description.removeViewer(player)
        }
    }
}