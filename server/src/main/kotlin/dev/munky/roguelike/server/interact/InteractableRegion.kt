package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.IcoSphere
import dev.munky.roguelike.common.Initializable
import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.*
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.math.ceil
import kotlin.math.floor

interface Region {
    /**
     * Axis-aligned bounding box for this shape. Can be used for fast broad-phase checks
     * or for generic intersection when only a common representation is desired.
     */
    val boundingBox: Cuboid

    fun offset(p: Vector3dc): Region

    fun expand(amount: Double): Region
    fun contains(p: Vector3dc): Boolean

    /**
     * Check whether this shape intersects with another shape.
     * Implementations should handle all supported shape pairs without requiring the caller to cast.
     */
    fun intersects(other: Region): Boolean

    /**
     * Generic intersection check using only the bounding boxes of shapes.
     * This is conservative: true means shapes may intersect (or definitely intersect),
     * false means they definitely do not intersect.
     */
    fun intersectsAabb(other: Region): Boolean = this.boundingBox.intersects(other.boundingBox)

    /**
     * Implementors should override this to cache the chunks this region resides in.
     */
    fun containedChunks(): LongArray {
        val min = boundingBox.min
        val max = boundingBox.max

        val minChunkX = CoordConversion.globalToChunk(floor(min.x()).toInt())
        val minChunkZ = CoordConversion.globalToChunk(floor(min.z()).toInt())
        val maxChunkX = CoordConversion.globalToChunk(ceil(max.x()).toInt())
        val maxChunkZ = CoordConversion.globalToChunk(ceil(max.z()).toInt())

        val chunksX = maxChunkX - minChunkX + 1
        val chunksZ = maxChunkZ - minChunkZ + 1

        val result = LongArray(chunksX * chunksZ)
        var i = 0
        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                result[i++] = CoordConversion.chunkIndex(x, z)
            }
        }
        return result
    }

    data class Sphere(val center: Vector3dc, val radius: Double) : Region {
        override fun expand(amount: Double): Region = copy(radius = radius + amount)

        override fun contains(p: Vector3dc): Boolean {
            val off = p.sub(center, Vector3d())
            return off.lengthSquared() <= radius * radius
        }

        override fun offset(p: Vector3dc): Region = Sphere(center.add(p, Vector3d()), radius)

        override val boundingBox: Cuboid by lazy {
            val r = radius
            val min = Vector3d(center).sub(r, r, r)
            val max = Vector3d(center).add(r, r, r)
            Cuboid(min, max)
        }

        override fun intersects(other: Region): Boolean = when (other) {
            is Sphere -> {
                val d2 = center.distanceSquared(other.center)
                val r = radius + other.radius
                d2 <= r * r
            }

            is Cuboid -> {
                // Sphere vs AABB: compute closest point on AABB to sphere center, check distance <= radius
                val cx = center.x()
                val cy = center.y()
                val cz = center.z()
                val qx = when {
                    cx < other.min.x() -> other.min.x()
                    cx > other.max.x() -> other.max.x()
                    else -> cx
                }
                val qy = when {
                    cy < other.min.y() -> other.min.y()
                    cy > other.max.y() -> other.max.y()
                    else -> cy
                }
                val qz = when {
                    cz < other.min.z() -> other.min.z()
                    cz > other.max.z() -> other.max.z()
                    else -> cz
                }
                val dx = cx - qx
                val dy = cy - qy
                val dz = cz - qz
                (dx * dx + dy * dy + dz * dz) <= radius * radius
            }

            else -> false
        }
    }

    data class Cuboid(val min: Vector3dc, val max: Vector3dc) : Region {
        override val boundingBox: Cuboid = this
        private var chunkCache: LongArray? = null

        override fun expand(amount: Double): Region = copy(
            min = min.sub(amount, amount, amount, Vector3d()),
            max = max.add(amount, amount, amount, Vector3d())
        )

        override fun contains(p: Vector3dc): Boolean {
            if (p.x() < min.x() || p.x() > max.x()) return false
            if (p.y() < min.y() || p.y() > max.y()) return false
            if (p.z() < min.z() || p.z() > max.z()) return false
            return true
        }

        override fun containedChunks(): LongArray {
            return chunkCache ?: super.containedChunks().also { chunkCache = it }
        }

        override fun offset(p: Vector3dc): Region = Cuboid(min.add(p, Vector3d()), max.add(p, Vector3d()))

        override fun intersects(other: Region): Boolean = when (other) {
            is Cuboid -> {
                // AABB vs AABB overlap (inclusive)
                !(max.x() < other.min.x() || min.x() > other.max.x()
                        || max.y() < other.min.y() || min.y() > other.max.y()
                        || max.z() < other.min.z() || min.z() > other.max.z())
            }

            is Sphere -> other.intersects(this)
            else -> false
        }
    }

    companion object {
        fun sphere(center: Vector3dc, radius: Double): Region = Sphere(center, radius)
        fun cuboid(p1: Vector3dc, p2: Vector3dc): Region {
            val min = p1.min(p2, Vector3d())
            val max = p1.max(p2, Vector3d())
            return Cuboid(min, max)
        }
    }
}

interface InteractableRegion {
    val region: Region
    val thickness: Double

    fun onEnter(player: RoguePlayer)
    fun onExit(player: RoguePlayer)

    class Dsl {
        private var region: Region? = null
        private var thickness: Double = 1.0
        private var onExit: (RoguePlayer) -> Unit = {}
        private var onEnter: (RoguePlayer) -> Unit = {}

        fun cuboid(from: Vector3dc, to: Vector3dc) {
            region = Region.cuboid(from, to)
        }

        fun sphere(center: Vector3dc, radius: Double) {
            region = Region.sphere(center, radius)
        }

        fun thickness(thickness: Double) {
            this.thickness = thickness
        }

        fun onExit(block: (RoguePlayer) -> Unit) {
            onExit = block
        }

        fun onEnter(block: (RoguePlayer) -> Unit) {
            onEnter = block
        }

        fun build(): InteractableRegion {
            val a = region ?: error("No area defined!")
            return InteractableRegionImpl(a, thickness, onExit, onEnter)
        }
    }

    companion object : Initializable {
        val EVENT_NODE: EventNode<PlayerEvent> =
            EventNode.event("${Roguelike.NAMESPACE}:interactable_area", EventFilter.PLAYER) { it.player is RoguePlayer }

        fun triggerAreas(areas: Collection<InteractableRegion>, player: RoguePlayer) {
            val justLeft = player.areasInside.mapNotNull {
                if (!it.value.contains(player.position.toJoml())) it.key else null
            }

            val justEntered = areas.filter {
                !player.areasInside.containsKey(it) && it.region.contains(player.position.toJoml())
            }

            for (area in justLeft) {
                area.onExit(player)
                player.areasInside.remove(area)
            }

            for (area in justEntered) {
                area.onEnter(player)
                player.areasInside[area] = area.region.expand(area.thickness)
            }
        }

        override suspend fun initialize() {
            Dispatchers.Default.launch {
                while (isActive) {
                    delay(100)
                    val instances = MinecraftServer.getInstanceManager().instances
                    coroutineScope {
                        for (instance in instances) if (instance is InteractableAreaContainer) launch {
                            synchronized(instance.players) {
                                for (player in instance.players.filterIsInstance<RoguePlayer>()) {
                                    triggerAreas(instance.areas, player)
                                }
                            }
                        }
                    }
                }
            }
            // debug visualization
            Dispatchers.Default.launch {
                // spheres are memory intense
                val sphereCache = HashMap<Region, Pair<IcoSphere, IcoSphere>>()
                while (isActive) {
                    delay(50)
                    for (instance in MinecraftServer.getInstanceManager().instances) {
                        val container = instance as? InteractableAreaContainer ?: continue
                        val areas = synchronized(container.areas) {
                            container.areas.toList()
                        }
                        for (a in areas) {
                            delay(50)
                            val shape = a.region
                            val innerParticle = Particle.DUST.withColor(Color(200, 0, 0))
                            val outerParticle = Particle.DUST.withColor(Color(0, 0, 200))
                            val particles = ArrayList<ParticlePacket>()
                            fun particle(p: Particle, x: Double, y: Double, z: Double) {
                                particles += ParticlePacket(p, x, y, z, 0f, 0f, 0f, 0f, 1)
                            }
                            when (shape) {
                                is Region.Cuboid -> {
                                    val step = 10
                                    fun drawCuboid(p: Particle, region: Region.Cuboid) {
                                        val ys = (region.max.y() - region.min.y()) / step
                                        var y = region.min.y()
                                        val xs = (region.max.x() - region.min.x()) / step
                                        var x = region.min.x()
                                        val zs = (region.max.z() - region.min.z()) / step
                                        var z = region.min.z()
                                        repeat(step) {
                                            particle(p, region.max.x(), y, region.min.z())
                                            particle(p, region.max.x(), y, region.max.z())
                                            particle(p, region.min.x(), y, region.min.z())
                                            particle(p, region.min.x(), y, region.max.z())

                                            y += ys
                                        }
                                        repeat(step) {
                                            particle(p, x, region.max.y(), region.min.z())
                                            particle(p, x, region.max.y(), region.max.z())
                                            particle(p, x, region.min.y(), region.min.z())
                                            particle(p, x, region.min.y(), region.max.z())

                                            x += xs
                                        }
                                        repeat(step) {
                                            particle(p, region.max.x(), region.max.y(), z)
                                            particle(p, region.max.x(), region.min.y(), z)
                                            particle(p, region.min.x(), region.max.y(), z)
                                            particle(p, region.min.x(), region.min.y(), z)

                                            z += zs
                                        }
                                    }
                                    drawCuboid(innerParticle, shape)
                                    drawCuboid(outerParticle, shape.expand(a.thickness) as Region.Cuboid)
                                }

                                is Region.Sphere -> {
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
                            for (p in instance.players.filterIsInstance<RoguePlayer>()) {
                                if (p.isDebug) p.sendPackets(particles as List<ParticlePacket>)
                            }
                            particles.clear()
                        }
                    }
                }
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}

data class InteractableRegionImpl(
    override val region: Region,

    /**
     * The thickness of the [region] so that the player
     * has a little buffer between exiting after entry.
     */
    override val thickness: Double = 2.0,
    val onExitFun: (RoguePlayer) -> Unit = {},
    val onEnterFun: (RoguePlayer) -> Unit = {}
) : InteractableRegion {
    override fun onExit(player: RoguePlayer) = onExitFun(player)
    override fun onEnter(player: RoguePlayer): Unit = onEnterFun(player)
}

interface InteractableAreaContainer {
    val areas: HashSet<InteractableRegion>

    fun createArea(b: InteractableRegion.Dsl.() -> Unit)
}