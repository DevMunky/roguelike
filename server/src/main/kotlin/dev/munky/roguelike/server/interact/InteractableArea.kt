package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.IcoSphere
import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.InteractableArea.Dsl
import dev.munky.roguelike.server.player.RoguelikePlayer
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.*
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import org.joml.Vector3d
import org.joml.Vector3dc

interface Shape {
    fun expand(amount: Double) : Shape
    fun contains(p: Vector3dc) : Boolean

    data class Sphere(val center: Vector3dc, val radius: Double) : Shape {
        override fun expand(amount: Double): Shape = copy(radius = radius + amount)

        override fun contains(p: Vector3dc): Boolean {
            val off = p.sub(center, Vector3d())
            return off.lengthSquared() <= radius * radius
        }
    }

    data class Cuboid(val min: Vector3dc, val max: Vector3dc) : Shape {
        override fun expand(amount: Double): Shape = copy(
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
        fun sphere(center: Vector3dc, radius: Double) : Shape = Sphere(center, radius)
        fun cuboid(p1: Vector3dc, p2: Vector3dc) : Shape {
            val min = p1.min(p2, Vector3d())
            val max = p1.max(p2, Vector3d())
            return Cuboid(min, max)
        }
    }
}

data class InteractableArea(
    val shape: Shape,

    /**
     * The thickness of the [shape] so that the player
     * has a little buffer between exiting after entry.
     */
    val thickness: Double = 2.0,
    val onExit: (RoguelikePlayer) -> Unit = {},
    val onEnter: (RoguelikePlayer) -> Unit = {}
) {
    class Dsl {
        private var shape: Shape? = null
        private var thickness: Double = 1.0
        private var onExit: (RoguelikePlayer) -> Unit = {}
        private var onEnter: (RoguelikePlayer) -> Unit = {}

        fun cuboid(from: Vector3dc, to: Vector3dc) {
            shape = Shape.cuboid(from, to)
        }
        fun sphere(center: Vector3dc, radius: Double) {
            shape = Shape.sphere(center, radius)
        }
        fun thickness(thickness: Double) {
            this.thickness = thickness
        }
        fun onExit(block: (RoguelikePlayer) -> Unit) {
            onExit = block
        }
        fun onEnter(block: (RoguelikePlayer) -> Unit) {
            onEnter = block
        }

        fun build() : InteractableArea {
            val a = shape ?: error("No area defined!")
            return InteractableArea(a, thickness, onExit, onEnter)
        }
    }

    companion object {
        val EVENT_NODE: EventNode<PlayerEvent> = EventNode.event("${Roguelike.NAMESPACE}:interactable_area", EventFilter.PLAYER) {
            it.player is RoguelikePlayer
        }

        fun area(block: Dsl.() -> Unit) = Dsl().apply(block).build()

        fun triggerAreas(areas: Collection<InteractableArea>, player: RoguelikePlayer) {
            val justLeft = player.areasInside.filter {
                !it.shape
                    .expand(it.thickness)
                    .contains(player.position.toJoml())
            }

            val justEntered = areas.filter {
                it !in player.areasInside
                        && it.shape.contains(player.position.toJoml())
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
            // debug visualization
            Dispatchers.Default.launch {
                // spheres are memory intense
                val sphereCache = HashMap<Shape, Pair<IcoSphere, IcoSphere>>()
                while(isActive) {
                    delay(100)
                    for (instance in MinecraftServer.getInstanceManager().instances) {
                        val container = instance as? InteractableAreaContainer ?: continue
                        for (a in container.areas) {
                            val shape = a.shape
                            val innerParticle = Particle.DUST.withColor(Color(200, 0, 0))
                            val outerParticle = Particle.DUST.withColor(Color(0, 0, 200))
                            val particles = ArrayList<ParticlePacket>()
                            fun particle(p: Particle, x: Double, y: Double, z: Double) {
                                particles += ParticlePacket(p, x, y, z, 0f, 0f, 0f, 0f, 1)
                            }
                            when (shape) {
                                is Shape.Cuboid -> {
                                    val step = 10
                                    fun drawCuboid(p: Particle, shape: Shape.Cuboid) {
                                        val ys = (shape.max.y() - shape.min.y()) / step
                                        var y = shape.min.y()
                                        val xs = (shape.max.x() - shape.min.x()) / step
                                        var x = shape.min.x()
                                        val zs = (shape.max.z() - shape.min.z()) / step
                                        var z = shape.min.z()
                                        repeat(step) {
                                            particle(p, shape.max.x(), y, shape.min.z())
                                            particle(p, shape.max.x(), y, shape.max.z())
                                            particle(p, shape.min.x(), y, shape.min.z())
                                            particle(p, shape.min.x(), y, shape.max.z())

                                            y += ys
                                        }
                                        repeat(step) {
                                            particle(p, x, shape.max.y(), shape.min.z())
                                            particle(p, x, shape.max.y(), shape.max.z())
                                            particle(p, x, shape.min.y(), shape.min.z())
                                            particle(p, x, shape.min.y(), shape.max.z())

                                            x += xs
                                        }
                                        repeat(step) {
                                            particle(p, shape.max.x(), shape.max.y(), z)
                                            particle(p, shape.max.x(), shape.min.y(), z)
                                            particle(p, shape.min.x(), shape.max.y(), z)
                                            particle(p, shape.min.x(), shape.min.y(), z)

                                            z += zs
                                        }
                                    }
                                    drawCuboid(innerParticle, shape)
                                    drawCuboid(outerParticle, shape.expand(a.thickness) as Shape.Cuboid)
                                }
                                is Shape.Sphere -> {
                                    val (inner, outer) = sphereCache.getOrPut(shape) {
                                        val inner = IcoSphere(2).also {
                                            it.mul(shape.radius)
                                        }
                                        val outer = IcoSphere(3).also {
                                            it.mul(shape.radius + a.thickness)
                                        }
                                        inner to outer
                                    }
                                    val c = shape.center
                                    for (p in inner.points) {
                                        particle(innerParticle, c.x() + p.x, c.y() + p.y, c.z() + p.z)
                                    }
                                    for (p in outer.points) {
                                        particle(outerParticle, c.x() + p.x, c.y() + p.y, c.z() + p.z)
                                    }
                                }
                            }
                            for (p in instance.players.filterIsInstance<RoguelikePlayer>()) {
                                if (p.isDebug) p.sendPackets(particles as List<ParticlePacket>)
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