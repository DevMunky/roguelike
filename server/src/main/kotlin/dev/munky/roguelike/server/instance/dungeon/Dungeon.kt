package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.logger
import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.sentenceCase
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.enemy.Enemy
import dev.munky.roguelike.server.enemy.Enemy.Source
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.generator.BacktrackingGenerator
import dev.munky.roguelike.server.instance.dungeon.generator.CandidateSolver
import dev.munky.roguelike.server.instance.dungeon.generator.GenerationOrchestrator
import dev.munky.roguelike.server.instance.dungeon.generator.Generator
import dev.munky.roguelike.server.instance.dungeon.generator.SpatialRegion
import dev.munky.roguelike.server.instance.dungeon.generator.PlannedRoom
import dev.munky.roguelike.server.instance.dungeon.generator.Tree
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomFeatures
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.town.TownInstance.Companion.TOWN_DIMENSION_KEY
import dev.munky.roguelike.server.interact.InteractableRegion
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.util.ParticleUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
     * Null if this dungeon has not been generated.
     */
    var rooms: Tree<Room>? = null

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
        rooms = null
        areas.clear()
        regionRenderHandles.forEach { map -> map.value.forEach { h -> h.value.dispose() } }
        MinecraftServer.getInstanceManager().unregisterInstance(this)
    }

    /**
     * Generate, the rooms, then paste them in the world and initialize appropriately.
     */
    private suspend fun initialize() {
        val stats = Generator.Stats()
        val spatial = SpatialRegion(stats = stats)
        val candidateSolver = CandidateSolver(spatialRegion = spatial, stats = stats)
        val orchestrator = GenerationOrchestrator(
            roomset = roomset,
            candidateSolver = candidateSolver,
            random = Random(System.nanoTime()).asJavaRandom(),
            generatorSupplier = ::BacktrackingGenerator
        )

        LOGGER.info("Generating roomset '${roomset.id}' for a dungeon.")
        val planTree = when (val result = orchestrator.generate()) {
            is dev.munky.roguelike.common.Result.Success -> result.value
            is dev.munky.roguelike.common.Result.Failure -> throw RuntimeException("Generation failed unexpectedly: ${result.reason}")
        }

        LOGGER.info("Committing plan for roomset '${roomset.id}'.")
        rooms = commitPlan(planTree)
        LOGGER.info("Done generating.")
    }

    private suspend fun createRoom(
        bp: RoomBlueprint,
        at: BlockVec,
        rotation: Rotation
    ): Room {
        val room = Room(
            this,
            bp,
            bp.featuresWith(rotation),
            at,
            bp.paste(this, at, rotation)
        ).also { areas += it }
        return room
    }

    /**
     * Paste room in the world and initialize it, then reference connections appropriately.
     */
    private suspend fun commitPlan(tree: Tree<PlannedRoom>): Tree<Room> {
        val realTree = tree.mapAsync { _ ->
            createRoom(blueprint, position, rotation)
        }
        return realTree
    }

    data class Room(
        val dungeon: Dungeon,
        val blueprint: RoomBlueprint,
        val features: RoomFeatures,
        val position: BlockVec,
        /**
         * The blocks this room occupies.
         */
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

        private val players = ArrayList<RoguePlayer>()

        /**
         * The enemies physically in this room
         */
        private var livingEnemies: HashSet<Enemy>? = null

        override val key: RenderContext.Key<*> = Companion
        override val thickness: Double = 0.666

        @Volatile
        var state = State.UNTOUCHED

        private var tickingJob: Job? = null

        private fun ensureTickingJobRunning() {
            if (tickingJob != null) return
            tickingJob = Dispatchers.Default.launch {
                while (isActive) {
                    delay(100)
                    players.forEach { p -> p.sendActionBar("Inside of room ${blueprint.id}: ${state.name.lowercase().sentenceCase()}".asComponent()) }
                }
            }
        }

        private fun checkTickingJob() {
            if (players.isNotEmpty() || tickingJob == null) return
            tickingJob?.cancel()
        }

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
            players += player
            ensureTickingJobRunning()
            if (state != State.UNTOUCHED) return
            state = State.ENTERED

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

                val enemy = enemyData.toEnemy(Source.DungeonRoom(this))

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
        }

        override fun onExit(player: RoguePlayer) {
            players -= player
            checkTickingJob()
        }

        enum class State {
            UNTOUCHED, ENTERED, COMPLETE
        }

        companion object : RenderContext.Key<Room> {
            private val EVENT_NODE = EventNode.all("${Roguelike.NAMESPACE}:dungeon_room")
        }
    }

    companion object {
        private val LOGGER = logger {}

        suspend fun create(roomset: RoomSet, players: List<RoguePlayer>): Dungeon = Dungeon(roomset).apply {
            initialize()
            MinecraftServer.getInstanceManager().registerInstance(this)
            isDebug = players.all { it.isDebug }
            for (player in players) {
                player.setInstance(this, Pos(.0, 110.0, .0))
            }
        }
    }
}