package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.interact.InteractableArea.Dsl
import dev.munky.roguelike.server.player.RoguelikePlayer
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import org.joml.Vector3d
import org.joml.Vector3dc
import java.time.Duration
import kotlin.collections.plus
import kotlin.collections.toHashSet
import kotlin.time.toJavaDuration

sealed interface Area {
    fun expand(amount: Double) : Area
    fun contains(p: Vector3dc) : Boolean

    data class Sphere(val center: Vector3dc, val radius: Double) : Area {
        override fun expand(amount: Double): Area = copy(radius = radius + amount)

        override fun contains(p: Vector3dc): Boolean {
            val off = p.sub(center, Vector3d())
            return off.lengthSquared() <= radius * radius
        }
    }

    data class Cuboid(val min: Vector3dc, val max: Vector3dc) : Area {
        override fun expand(amount: Double): Area = copy(
            min = min.sub(Vector3d(amount), Vector3d()),
            max = max.add(Vector3d(amount), Vector3d())
        )

        override fun contains(p: Vector3dc): Boolean {
            if (p.x() < min.x() || p.x() > max.x()) return false
            if (p.y() < min.y() || p.y() > max.y()) return false
            if (p.z() < min.z() || p.z() > max.z()) return false
            return true
        }
    }

    companion object {
        fun sphere(center: Vector3dc, radius: Double) : Area = Sphere(center, radius)
        fun cuboid(p1: Vector3dc, p2: Vector3dc) : Area {
            val min = p1.min(p2, Vector3d())
            val max = p1.max(p2, Vector3d())
            return Cuboid(min, max)
        }
    }
}

interface InteractableArea {
    val area: Area

    /**
     * The thickness of the [area] so that the player
     * has a little buffer between exiting after entry.
     */
    val thickness: Double get() = 2.0

    /**
     * The time it between entering the area and [onEnter] being called.
     */
    val onEnterCooldown: Duration

    fun onExit(player: RoguelikePlayer) {}
    fun onEnter(player: RoguelikePlayer) {}

    class Dsl {
        private var area: Area? = null
        private var thickness: Double = 1.0
        private var onEnterCooldown: Duration? = null
        private var onExit: (RoguelikePlayer) -> Unit = {}
        private var onEnter: (RoguelikePlayer) -> Unit = {}

        fun cuboid(from: Vector3dc, to: Vector3dc) {
            area = Area.cuboid(from, to)
        }
        fun sphere(center: Vector3dc, radius: Double) {
            area = Area.sphere(center, radius)
        }
        fun thickness(thickness: Double) {
            this.thickness = thickness
        }
        fun bufferTime(duration: kotlin.time.Duration) {
            this.onEnterCooldown = duration.toJavaDuration()
        }
        fun onExit(block: (RoguelikePlayer) -> Unit) {
            onExit = block
        }
        fun onEnter(block: (RoguelikePlayer) -> Unit) {
            onEnter = block
        }

        fun build() : InteractableArea {
            val a = area ?: error("No area defined!")
            val b = onEnterCooldown ?: error("No buffer time defined!")
            return object : InteractableArea {
                override val area: Area = a
                override fun onEnter(player: RoguelikePlayer) = this@Dsl.onEnter(player)
                override fun onExit(player: RoguelikePlayer) = this@Dsl.onExit(player)
                override val onEnterCooldown: Duration = b
                override val thickness: Double = this@Dsl.thickness
            }
        }
    }

    companion object {
        val EVENT_NODE: EventNode<PlayerEvent> = EventNode.event("roguelike:interactable_area", EventFilter.PLAYER) {
            it.player is RoguelikePlayer
        }

        fun area(block: Dsl.() -> Unit) = Dsl().apply(block).build()

        fun triggerAreas(areas: Collection<InteractableArea>, player: RoguelikePlayer) {
            val justLeft = player.areasInside.filter {
                !it.area
                    .expand(it.thickness)
                    .contains(player.position.toJoml())
            }

            val justEntered = areas.filter {
                it !in player.areasInside
                        && it.area.contains(player.position.toJoml())
            }

            for (area in justLeft) area.onExit(player)
            for (area in justEntered) area.onEnter(player)

            player.areasInside.removeAll(justLeft.toSet())
            player.areasInside.addAll(justEntered)
        }

        fun initialize() {
            Dispatchers.Default.launch {
                while (isActive) {
                    delay(100)
                    val instances = MinecraftServer.getInstanceManager().instances
                    coroutineScope {
                        for (instance in instances) if (instance is InteractableAreaContainer) launch {
                            for (player in instance.players.filterIsInstance<RoguelikePlayer>()) {
                                triggerAreas(instance.areas, player)
                            }
                        }
                    }
                }
            }
            Dispatchers.Default.launch {
                while(isActive) {
                    delay(100)
                    for (instance in MinecraftServer.getInstanceManager().instances) {
                        val container = instance as? InteractableAreaContainer ?: continue
                        for (a in container.areas) {
                            val shape = a.area
                            val part = Particle.DUST.withColor(Color(200, 0, 0))
                            val particles = ArrayList<ParticlePacket>()
                            when (shape) {
                                is Area.Cuboid -> {
                                    for (x in shape.min.x().toInt()..shape.max.x().toInt()) {
                                        for (y in shape.min.y().toInt()..shape.max.y().toInt()) {
                                            for (z in shape.min.z().toInt()..shape.max.z().toInt()) {
                                                val packet = ParticlePacket(part, x.toDouble(), y.toDouble(),
                                                    z.toDouble(), 0f, 0f, 0f, 0f, 1)
                                                particles += packet
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                            for (p in instance.players) {
                                p.sendPackets(particles as List<ParticlePacket>)
                            }
                        }
                    }
                }
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}

interface InteractableAreaContainer {
    val areas: HashSet<InteractableArea>

    fun createArea(b: Dsl.() -> Unit)
}