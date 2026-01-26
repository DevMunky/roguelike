package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.Result
import dev.munky.roguelike.common.mapSuccess
import dev.munky.roguelike.common.toFailure
import dev.munky.roguelike.common.toSuccess
import dev.munky.roguelike.server.instance.dungeon.generator.Generator.Failure
import dev.munky.roguelike.server.instance.dungeon.roomset.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.interact.Region
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import java.util.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class BacktrackingGenerator(assembledGenerator: GenerationOrchestrator) : Generator(assembledGenerator) {
    override suspend fun satisfyConnection(
        room: PlannedRoom,
        depth: Int,
        candidates: List<CandidateSolver.CandidateResult>
    ): Result<PlannedRoom, Failure>  {
        if (depth > 100) return Failure.DEPTH_EXCEEDED.toFailure()
        if (candidates.isEmpty()) return Failure.NO_VALID_CONNECTION.toFailure()

        // Return only the first viable candidate; strategies can override to reorder/filter
        val candidate = candidates.first()
        return candidate.toPlannedRoom().toSuccess()
    }
}

abstract class Generator(
    protected val assembledGenerator: GenerationOrchestrator
) {
    abstract suspend fun satisfyConnection(
        room: PlannedRoom,
        depth: Int,
        candidates: List<CandidateSolver.CandidateResult>
    ): Result<PlannedRoom, Failure>

    enum class Failure {
        DEPTH_EXCEEDED,
        NO_VALID_CONNECTION,
        NO_POOL,
        EMPTY_POOL,
        TOO_SMALL,
    }

    data class Stats(
        var roomsPlanned: Long = 0L,
        var chunksChecked: Long = 0L,

        var descends: Long = 0L,
        var ascends: Long = 0L,

        var intersectionsChecked: Long = 0L,
        var intersections: Long = 0L,

        var candidatesTried: Long = 0L,
        var spatialBinAccesses: Long = 0L,
        var spatialBinCopies: Long = 0L,
        var spatialBinAverageCopySize: Double = .0,
        var spatialBinMaxSize: Long = 0L,

        var heightBoundsFails: Long = 0L,
        var timeTaken: Duration = 0.milliseconds,
    )
}

open class GenerationOrchestrator(
    private val roomset: RoomSet,
    val candidateSolver: CandidateSolver,
    val random: Random,
    generatorSupplier: (GenerationOrchestrator)->Generator,
    val startPosition: BlockVec = BlockVec(0, 100, 0),
) {
    private val generator = generatorSupplier(this)
    private lateinit var result: Tree<PlannedRoom>

    var stats = Generator.Stats()

    suspend fun generate(): Result<Tree<PlannedRoom>, Failure> {
        stats = Generator.Stats()
        candidateSolver.ensureReady()
        val start = TimeSource.Monotonic.markNow()
        val root = planRoot()

        result = Tree(root)
        commitRoot(root)

        val returned = generate(root, 0)

        stats.timeTaken = start.elapsedNow()
        return returned.mapSuccess { result }
    }

    protected suspend fun generate(parent: PlannedRoom, depth: Int): Result<PlannedRoom, Failure> {
        val openConnections = parent.connections.openConnections
        if (openConnections.isEmpty()) {
            return parent.toSuccess()
        }

        for (connection in openConnections.toHashSet()) {
            val pool = connection.pool ?: return Failure.NO_POOL.toFailure()
            if (pool.entries.isEmpty()) return Failure.EMPTY_POOL.toFailure()
            // Ask generator to select a candidate (no side effects)
            // Sample the pool deterministically using the orchestrator's Random
            val sampleSize = pool.entries.size
            val sample = LinkedHashSet<String>(sampleSize)
            repeat(sampleSize) {
                sample.add(pool.entries.weightedRandom(random))
            }

            val candidates = candidateSolver.computeCandidates(parent, connection, pool, sample)

            when (val r = generator.satisfyConnection(parent, depth + 1, candidates)) {
                is Result.Success -> {
                    val child = r.value
                    commit(parent, connection, child)
                    generate(child, depth + 1)
                }
                is Result.Failure -> {
                    return r
                }
            }
        }

        return parent.toSuccess()
    }

    private fun commitRoot(root: PlannedRoom) {
        // Index spatial region for root
        candidateSolver.spatialRegion.index(root.bounds)
        stats.roomsPlanned++
    }

    private fun commit(parent: PlannedRoom, via: ConnectionFeature, child: PlannedRoom) {
        val parentNode = result.getNode(parent) ?: error("Room '${parent.blueprint.id}' not in tree.")
        // Add child node to tree
        result.addNode(parentNode, child)

        parent.connections.setClosed(via)

        val matched = child.matchedConnection
            ?: error("Child '${child.blueprint.id}' did not specify matchedConnection")

        child.connections.setClosed(matched)
        child.usedParentConnection = via

        // Index spatial region for child
        candidateSolver.spatialRegion.index(child.bounds)
        stats.roomsPlanned++
    }

    @Suppress("unused")
    private fun revert(child: PlannedRoom) {
        stats.roomsPlanned--
        // Unindex spatial region for child
        candidateSolver.spatialRegion.unindex(child.bounds)

        val node = result.getNode(child) ?: return
        // Remove from tree (with subtree)
        result.removeNode(node)
    }

    protected fun planRoot() : PlannedRoom {
        val blueprint = roomset.rooms[roomset.rootRoomId]
            ?: error("No root room '${roomset.rootRoomId}' defined.")

        // Commiting the root room with any other rotation does not align the bounds correctly.
        val rotation = Rotation.CLOCKWISE_90
        val bounds = blueprint.boundsAt(startPosition, rotation)

        return PlannedRoom(
            blueprint = blueprint,
            position = BlockVec(0, 100, 0),
            rotation = rotation,
            bounds = bounds
        )
    }
}

data class PlannedRoom(
    val blueprint: RoomBlueprint,
    val position: BlockVec,
    val rotation: Rotation,
    val bounds: Region = blueprint.boundsAt(position, rotation),
    val matchedConnection: ConnectionFeature? = null
) {
    val connections: Connections = Connections()
    // The connection on the parent room that this room attached to (set by orchestrator)
    var usedParentConnection: ConnectionFeature? = null

    fun computeCandidatePosition(
        fromConnection: ConnectionFeature,
        candidateConnection: ConnectionFeature
    ): BlockVec {
        val connectorPos = fromConnection.position.add(position)
        val otherPos = connectorPos.add(
            fromConnection.direction.normalX(),
            fromConnection.direction.normalY(),
            fromConnection.direction.normalZ()
        )
        return otherPos.sub(candidateConnection.position)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlannedRoom) return false

        if (blueprint != other.blueprint) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blueprint.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }

    override fun toString(): String {
        return "PlannedRoom(bounds=$bounds, rotation=$rotation, position=$position, blueprint=$blueprint, connections=$connections"
    }

    inner class Connections {
        val allConnections = blueprint.featuresWith(rotation).connections

        private var _openConnections = HashSet<ConnectionFeature>(allConnections)
        val openConnections: Set<ConnectionFeature> get() = _openConnections

        fun setOpen(connection: ConnectionFeature) {
            assertWithinRoom(connection)
            _openConnections += connection
        }

        fun setClosed(connection: ConnectionFeature) {
            assertWithinRoom(connection)
            _openConnections -= connection
        }

        private fun assertWithinRoom(connection: ConnectionFeature) {
            if (connection !in allConnections) error("Connection '$connection' not part of this room.")
        }

        override fun toString(): String {
            return "Connections(openConnections=$openConnections)"
        }
    }
}