package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.enemy.Enemy
import dev.munky.roguelike.server.enemy.Enemy.Source
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.roomset.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomFeatures
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.town.TownInstance.Companion.TOWN_DIMENSION_KEY
import dev.munky.roguelike.server.interact.InteractableRegion
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.util.ParticleUtil
import net.hollowcube.schem.util.Rotation
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDeathEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.particle.Particle
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.random.Random
import kotlin.random.asJavaRandom

class Dungeon private constructor(
    val roomset: RoomSet
) : RogueInstance(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
    var isDebug = true

    init {
        chunkSupplier = { i, x, z ->
            LightingChunk(i, x, z)
        }
    }

    /**
     * Null if this dungeon has not yet been generated.
     */
    var rootRoom: Room? = null
        private set

    override fun onEnter(player: RoguePlayer) {
        player.gameMode = GameMode.CREATIVE
        player.permissionLevel = 4
        super.onEnter(player)
    }

    override fun onExit(player: RoguePlayer) {
        player.gameMode = GameMode.SURVIVAL
        player.permissionLevel = 4
        super.onExit(player)
        if (players.isEmpty()) {
            shutdown()
        }
    }

    private fun shutdown() {
        rootRoom = null
        areas.clear()
        regionRenderHandles.forEach { map -> map.value.forEach { h -> h.value.dispose() } }
        MinecraftServer.getInstanceManager().unregisterInstance(this)
    }

    private suspend fun initialize() {
        // place root room at origin
        val generator = BackTrackingGenerator(roomset, maxDepth = 50, seed = System.nanoTime(), debug = isDebug)
        try {
            val plan = when (val generation = generator.plan()) {
                Generator.Result.Failure.NO_POOL -> throw RuntimeException("No pool available.")
                Generator.Result.Failure.EMPTY_POOL -> throw RuntimeException("A pool is empty.")
                Generator.Result.Failure.DEPTH_EXCEEDED -> throw RuntimeException("Exceeded max depth.")
                Generator.Result.Failure.NO_VALID_CONNECTION -> throw RuntimeException("No valid connection found.")
                Generator.Result.Failure.TOO_SMALL -> throw RuntimeException("Dungeon too small.")
                is Generator.Result.Success -> generation.room
            }

            LOGGER.info("Commiting plan for roomset '${roomset.id}'.")
            rootRoom = commitPlan(plan)

            LOGGER.info("Done generating.")
        } catch (t: Throwable) {
            throw RuntimeException("Exception caught initializing dungeon.", t)
        } finally {
            LOGGER.debug("Generation planning stats = {}", generator.stats)
        }
    }

    private suspend fun createRoom(
        bp: RoomBlueprint,
        connections: MutableMap<ConnectionFeature, Room?>,
        at: BlockVec,
        rotation: Rotation
    ): Room {
        val features = bp.featuresWith(rotation)
        val region = bp.paste(this@Dungeon, at, rotation)
        val room = Room(this, bp, features, connections, at, region).also { areas += it }
        return room
    }

    private suspend fun commitPlan(root: PlannedRoom): Room {
        // 1st pass: paste rooms
        val plannedToReal = IdentityHashMap<PlannedRoom, Room>()
        val stack = ArrayDeque<PlannedRoom>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val pr = stack.removeLast()
            if (plannedToReal.containsKey(pr)) continue

            val connections = HashMap<ConnectionFeature, Room?>(pr.connections.size)
            pr.blueprint.featuresWith(pr.rotation).connections.associateWithTo(connections) { null as Room? }

            val room = createRoom(pr.blueprint, connections, pr.position, pr.rotation)
            plannedToReal[pr] = room

            // enqueue children
            for (child in pr.connections.values) {
                if (child != null) stack.add(child)
            }
        }

        // 2nd pass: wire connections in both directions
        for ((pr, real) in plannedToReal.entries) {
            for ((c, child) in pr.connections) {
                if (child != null) {
                    val childReal = plannedToReal[child] ?: continue
                    // Defensive: avoid self-referential connection which can cause recursion/stack overflows
                    if (childReal === real) continue
                    real.connections[c] = childReal
                    // find reverse key in child
                    val reverseKey = child.connections.entries.firstOrNull { it.value === pr }?.key
                    if (reverseKey != null) {
                        childReal.connections[reverseKey] = real
                    }
                }
            }
        }

        return plannedToReal[root]!!
    }

    data class Room(
        val dungeon: Dungeon,
        val blueprint: RoomBlueprint,
        val features: RoomFeatures,
        /**
         * The connections of this room in the world.
         */
        val connections: MutableMap<ConnectionFeature, Room?>,
        val position: BlockVec,
        override val region: Region
    ) : InteractableRegion, RenderContext.Element {
        private val instanceEventNode = EventNode.type("${dungeon.roomset.id}.${blueprint.id}.${position.x()}-${position.y()}-${position.z()}", EventFilter.ENTITY).apply {
            addListener(EntityDeathEvent::class.java) {
                if (it.instance != dungeon) return@addListener
                val enemy = it.entity as? Enemy ?: return@addListener
                // we are rest assured that this event node is being called once the entityList is initialized
                val room = (enemy.source as? Source.DungeonRoom)?.room ?: return@addListener
                // the attacker is not in this event, but LivingEntity.lastDamageSource exists
                room.enemyDeath(enemy)
            }
        }
        private var livingEnemies: HashSet<Enemy>? = null

        override val key: RenderContext.Key<*> = Companion
        override val thickness: Double = 0.666
        var state = State.UNTOUCHED

        private fun enemyDeath(enemy: Enemy) {
            val living = livingEnemies ?: error("Enemy (${enemy.data}) killed before room '$this' livingEntities list is set.")
            living.remove(enemy)
            if (living.isEmpty()) {
                ParticleUtil.drawParticlesAround(dungeon, enemy, Particle.SOUL, expansion = 1.05, amount = 20)
                val attacker = enemy.lastDamageSource?.source as? RoguePlayer
                if (attacker != null) {
                    ParticleUtil.drawParticlesAround(dungeon, attacker, Particle.SOUL, expansion = 1.05, amount = 20)
                }
                state = State.COMPLETE
            }
        }

        override fun onEnter(player: RoguePlayer) {
            val enemyFeatures = features.enemies
            val enemies = HashSet<Enemy>()
            for (enemyFeature in enemyFeatures) {
                val enemyId = enemyFeature.pool?.entries?.weightedRandom(Random.asJavaRandom()) ?: run {
                    LOGGER.warn("Enemy feature $enemyFeature has an invalid pool '${enemyFeature.poolName}'.")
                    continue
                }
                val enemyData = Roguelike.server().enemies()[enemyId] ?: run {
                    LOGGER.warn("Enemy '$enemyId' from pool '${enemyFeature.poolName}' does not exist.")
                    continue
                }
                val enemy = Enemy(enemyData, Source.DungeonRoom(this))

                val normalX = enemyFeature.direction.normalX()
                val normalZ = enemyFeature.direction.normalZ()
                val yaw = normalX * 90f + when (normalZ) {
                    -1, 0 -> 0f
                    else -> 180f
                }

                enemy.setInstance(dungeon, enemyFeature.position)
                enemy.setView(yaw, 0f)
                enemies.add(enemy)
            }

            livingEnemies = enemies
            EVENT_NODE.addChild(instanceEventNode)
            state = State.ENTERED
            player.sendMessage("Entered room ${blueprint.id}")
        }

        override fun onExit(player: RoguePlayer) {
            player.sendMessage("Exited room ${blueprint.id}")
        }

        enum class State {
            UNTOUCHED, ENTERED, COMPLETE
        }

        companion object : RenderContext.Key<Room> {
            private val EVENT_NODE = EventNode.all("roguelike:dungeon_room")
        }
    }

    companion object {
        private val LOGGER = logger {}

        suspend fun create(roomset: RoomSet, players: List<RoguePlayer>): Dungeon = Dungeon(roomset).apply {
            initialize()
            MinecraftServer.getInstanceManager().registerInstance(this)
            if (players.all { it.isDebug }) isDebug = true
            for (player in players) {
                player.setInstance(this, Pos(.0, 110.0, .0))
            }
        }
    }
}
