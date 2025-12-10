package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandle
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandleManager
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.roomset.JigsawConnection
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.town.TownInstance.Companion.TOWN_DIMENSION_KEY
import dev.munky.roguelike.server.interact.InteractableRegion
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.player.RoguePlayer
import net.hollowcube.schem.util.Rotation
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.instance.LightingChunk
import java.util.*

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
        val generator = BackTrackingGenerator(roomset, maxDepth = 50, seed = System.nanoTime(), debug = false)
        try {
            val plan = when (val generation = generator.plan()) {
                Generator.Result.Failure.NO_POOL -> throw RuntimeException("No pool available..")
                Generator.Result.Failure.DEPTH_EXCEEDED -> throw RuntimeException("Exceeded max depth.")
                Generator.Result.Failure.NO_VALID_CONNECTION -> throw RuntimeException("No valid connection found.")
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
        connections: MutableMap<JigsawConnection, Room?>,
        at: BlockVec,
        rotation: Rotation
    ): Room {
        val region = bp.paste(this@Dungeon, at, rotation)
        val room = Room(bp, connections, at, region).also { areas += it }
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

            val connections = HashMap<JigsawConnection, Room?>(pr.connections.size)
            pr.blueprint.connectionsWith(pr.rotation).associateWithTo(connections) { null as Room? }

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

    class Room(
        val blueprint: RoomBlueprint,
        /**
         * The connections of this room in the world.
         */
        val connections: MutableMap<JigsawConnection, Room?>,
        val position: BlockVec,
        override val region: Region
    ) : InteractableRegion, RenderHandleManager, RenderContext.Element {
        override val key: RenderContext.Key<*> = Companion
        val renderHandles = HashMap<RoguePlayer, ArrayList<RenderHandle>>()

        override fun RenderDispatch.dispatchManaged() {
            val player = data[RoguePlayer] as? RoguePlayer ?: return
            renderHandles.getOrPut(player, ::ArrayList).add(dispatch())
        }

        override val thickness: Double = 0.666

        override fun onEnter(player: RoguePlayer) {
            player.sendMessage("Entered room ${blueprint.id}")
        }

        override fun onExit(player: RoguePlayer) {
            renderHandles.remove(player)?.forEach { it.dispose() }
            player.sendMessage("Exited room ${blueprint.id}")
        }

        companion object : RenderContext.Key<Room>
    }

    companion object {
        val LOGGER = logger {}

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