package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.roomset.JigsawConnection
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.town.TownInstance.Companion.TOWN_DIMENSION_KEY
import dev.munky.roguelike.server.interact.InteractableRegion
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.toJoml
import kotlinx.coroutines.withTimeoutOrNull
import net.hollowcube.schem.util.Rotation
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Area
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.entity.GameMode
import net.minestom.server.instance.LightingChunk
import java.util.*

class Dungeon private constructor(
    val roomset: RoomSet
) : RogueInstance(UUID.randomUUID(), TOWN_DIMENSION_KEY) {
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
        if (players.isEmpty()) {
            MinecraftServer.getInstanceManager().unregisterInstance(this)
        }
        super.onExit(player)
    }

    private suspend fun initialize() {
        // place root room at origin
        try {
            val rootData = roomset.rooms[RoomSet.ROOT_ROOM_ID] ?: error("No root room '${RoomSet.ROOT_ROOM_ID}' defined.")
            try {
                rootRoom = createRoom(
                    bp = rootData,
                    at = BlockVec.ZERO,
                    rotation = Rotation.NONE
                )
                withTimeoutOrNull(10000) {
                    generate(rootRoom!!, 3)
                } ?: error("Took too long to generate.")
            } catch (t: Throwable) {
                throw RuntimeException("Failed to generate.", t)
            }
        } catch (t: Throwable) {
            throw RuntimeException("Exception caught initializing dungeon.", t)
        }
    }

    private suspend fun generate(room: Room, depth: Int) {
        if (depth <= 0) return
        val pools = roomset.data.pools
        val connections = room.connections.takeIf { it.isNotEmpty() } ?: run {
            LOGGER.warn("No connections defined for room '${room.blueprint.id}'.")
            return
        }
        for (connection in connections.mapNotNull { if (it.value != null) null else it.key }) {
            val invertedDirection = connection.direction.opposite()
            val pool = pools[connection.pool] ?: error("No pool '${connection.pool}' defined.")
            if (pool.isEmpty()) {
                LOGGER.warn("Pool '${connection.pool}' in Roomset '${roomset.id}' is empty.")
                continue
            }
            var tries = pool.size
            var connected = false
            var randomRoomId = pool.random()
            while (!connected && tries > 0) {
                val other = roomset.rooms[randomRoomId]!! // RoomSetData checked

                var rotation = Rotation.entries.first()
                while (!connected) {
                    val otherConnections = other.connectionsBy(rotation)
                    for (otherConnection in otherConnections) {
                        if (otherConnection.pool != connection.pool) continue
                        if (otherConnection.direction != invertedDirection) continue
                        // align positions

                        // connectorPos is the world position of this room's jigsaw block
                        val connectorPos = connection.position.add(room.position)
                        // where the other room's jigsaw should be.
                        val otherConnectionPos = connectorPos.add(connection.direction.normalX(), connection.direction.normalY(), connection.direction.normalZ())
                        val otherRoomPos = otherConnectionPos.sub(otherConnection.position).asBlockVec()

                        // check bounds
                        val otherBounds = other.boundsBy(otherRoomPos, rotation)
                        // assume the root room is already present if we are generating from it
                        if (rootRoom!!.intersectsWithChildren(otherBounds)) {
                            LOGGER.info("Skipping connection '${otherConnection.name}' in room '${other.id}': intersection.")
                            continue
                        }
                        // Does not intersect with any existing room. Big speed opportunities here
                        // A spatial hierarchy would help a lot, but that's a later issue

                        // The room doesn't intersect with anything, let's create it.
                        val newRoom = createRoom(other, otherConnections.associateWith { null }.toMutableMap(), otherRoomPos, rotation)
                        newRoom.connections[otherConnection] = room
                        room.connections[connection] = newRoom
                        try {
                            generate(newRoom, depth - 1) // depth first, whatever.
                        } catch (t: Throwable) {
                            throw RuntimeException("Failed to generate room '${newRoom.blueprint.id}'.", t)
                        }
                        connected = true
                        break
                    }
                    if (rotation == Rotation.entries.last()) break
                    rotation = Rotation.entries[rotation.ordinal + 1]
                }
                if (!connected) {
                    tries -= 1
                    randomRoomId = pool.random()
                }
            }
            if (!connected) error("Connection '${connection.name}' for pool '${connection.pool}' in room '${room.blueprint.id}' found no valid connections.")
        }
    }

    private suspend fun createRoom(
        bp: RoomBlueprint,
        connections: MutableMap<JigsawConnection, Room?> = bp.connectionsBy(Rotation.CLOCKWISE_90).associateWith { null }.toMutableMap(),
        at: BlockVec,
        rotation: Rotation
    ) : Room {
        val area = bp.paste(this@Dungeon, at, rotation)
        return Room(bp, connections, at, area).also { areas += it }
    }

    @Suppress("UnstableApiUsage")
    class Room(
        val blueprint: RoomBlueprint,
        /**
         * The connections of this room in the world.
         */
        val connections: MutableMap<JigsawConnection, Room?>,
        val position: BlockVec,
        val area: Area
    ) : InteractableRegion {

        override val region: Region = Region.Cuboid(area.bound().min().toJoml(), area.bound().max().toJoml())
        override val thickness: Double = 0.666

        fun intersectsWith(other: Area) = area.intersect(other).isEmpty()
        fun intersectsWithChildren(other: Area) : Boolean {
            if (intersectsWith(other)) return true
            for (child in connections.values) {
                if (child?.intersectsWithChildren(other) ?: false) return true
            }
            return false
        }

        override fun onEnter(player: RoguePlayer) {
            player.sendMessage("Entered room ${blueprint.id}")
        }

        override fun onExit(player: RoguePlayer) {
            player.sendMessage("Exited room ${blueprint.id}")
        }
    }

    companion object {
        val LOGGER = logger {}

        suspend fun create(roomset: RoomSet) : Dungeon = Dungeon(roomset).apply {
            runCatching { initialize() }.onFailure { LOGGER.error("Failed to initialize dungeon.", it) }
            MinecraftServer.getInstanceManager().registerInstance(this)
        }
    }
}