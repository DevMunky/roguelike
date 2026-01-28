package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.Result
import dev.munky.roguelike.common.handleFailure
import dev.munky.roguelike.common.mapSuccess
import dev.munky.roguelike.common.toFailure
import dev.munky.roguelike.common.toSuccess
import dev.munky.roguelike.server.instance.dungeon.generator.Generator.Failure
import dev.munky.roguelike.server.instance.dungeon.roomset.ConnectionFeature
import dev.munky.roguelike.server.instance.dungeon.roomset.Pool
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.interact.Region
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomBlueprint
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.Random
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class BacktrackingGenerator(orchestrator: GenerationOrchestrator) : Generator(orchestrator) {
    private var rootPosition = BlockVec(0)

    override suspend fun run(root: PlannedRoom): Result<Unit, Failure> {
        var state = State.PICK_CONNECTION
        val stack = ArrayDeque<Frame>()

        stack.addLast(Frame(root))
        rootPosition = root.position

        main@ while (stack.isNotEmpty()) {
            val depth = stack.size
            val frame = stack.last()
            val room = frame.room

            val open = room.connections.openConnections

            when (state) {
                State.DONE -> {
                    if (stack.size == 1)
                        return Unit.toSuccess()
                    stack.removeLast()
                    state = State.PICK_CONNECTION
                }
                State.PICK_CONNECTION -> {
                    if (open.isEmpty()) {
                        state = State.DONE
                        continue@main
                    }
                    frame.via = chooseNextConnection(room, open)
                    state = State.PICK_CANDIDATE
                }
                State.PICK_CANDIDATE -> {
                    val connection = frame.via!!.handleFailure {
                        state = State.BACKTRACK
                        continue@main
                    }
                    val pool = connection.pool!!

                    val candidates = orchestrator.computeCandidates(room, connection, pool)

                    frame.candidate = satisfyConnection(room, connection, depth, candidates)
                    state = State.PLAN
                }
                State.PLAN -> {
                    val viaResult = frame.via
                    val candidateResult = frame.candidate
                    if (viaResult == null || candidateResult == null) {
                        state = State.BACKTRACK
                        continue@main
                    }
                    val via = viaResult.handleFailure {
                        state = State.BACKTRACK
                        continue@main
                    }
                    val candidate = candidateResult.handleFailure {
                        state = State.BACKTRACK
                        continue@main
                    }
                    orchestrator.commit(room, via, candidate)
                    stack.add(Frame(candidate))
                    state = State.PICK_CONNECTION
                }
                State.BACKTRACK -> {
                    if (depth == 1) {
                        stack.clear()
                        // reset root.
                        stack.addLast(Frame(root))
                        // reset orchestrator state.
                        orchestrator.reset()
                        state = State.PICK_CONNECTION
                        continue@main
                    }
                    val failed = stack.removeLast()
                    orchestrator.revert(failed.room)

                    //if (depth < 50) println("($depth) ${room.blueprint.id} backtracked")
                    // println("($depth) Backtracking from ${failed.room.blueprint.id} (via=${failed.via?.asFailure()}, candidate=${failed.candidate?.asFailure()})")

                    val top = stack.last()
                    if (top.fails > 2) {
                        // if the top of the stack has already failed before, backtrack that one too.
                        state = State.BACKTRACK
                    } else {
                        top.fails++
                        state = State.PICK_CANDIDATE
                    }
                }
            }
        }

        return Failure.NO_VALID_CONNECTION.toFailure()
    }

    override fun satisfyConnection(
        room: PlannedRoom,
        via: ConnectionFeature,
        depth: Int,
        candidates: List<CandidateSolver.CandidateResult>
    ): Result<PlannedRoom, Failure>  {
        val maxDepth = 25
        if (depth > maxDepth) return Failure.DEPTH_EXCEEDED.toFailure()
        val noTerminal = depth < maxDepth * .5
        val branchOut = false //depth < maxDepth * .8

        if (branchOut) {
            val dir = via.direction
            val shiftedRootPos = rootPosition.sub(via.position)
            val desirePosX = shiftedRootPos.x() < 0
            val desirePosZ = shiftedRootPos.z() < 0

            // If the direction normals don't match the desired ones.
            if (desirePosX && dir.normalX() < 0) {
                return Failure.NO_VALID_CONNECTION.toFailure()
            } else if (!desirePosX && dir.normalX() > 0) {
                return Failure.NO_VALID_CONNECTION.toFailure()
            }

            if (desirePosZ && dir.normalZ() < 0) {
                return Failure.NO_VALID_CONNECTION.toFailure()
            } else if (!desirePosZ && dir.normalZ() > 0) {
                return Failure.NO_VALID_CONNECTION.toFailure()
            }
        }

        // Return only the first viable candidate; strategies can override to reorder/filter
        var filteredCandidates = if (noTerminal) candidates.filter { !it.blueprint.isTerminal } else null
        filteredCandidates = filteredCandidates ?: candidates

        if (filteredCandidates.isEmpty()) return Failure.NO_VALID_CONNECTION.toFailure()
        return weightedRandom(filteredCandidates)!!.toPlannedRoom().toSuccess()
    }

    override fun chooseNextConnection(
        room: PlannedRoom,
        openConnections: Set<ConnectionFeature>
    ): Result<ConnectionFeature, Failure> {
//        val next = openConnections.minByOrNull { conn ->
//            val size = conn.pool?.entries?.size ?: Int.MAX_VALUE
//            size
//        }
        return openConnections.randomOrNull()?.toSuccess() ?: Failure.NO_VALID_CONNECTION.toFailure()
    }

    enum class State {
        PICK_CONNECTION,
        PICK_CANDIDATE,
        PLAN,
        BACKTRACK,
        DONE,
    }

    data class Frame(
        val room: PlannedRoom,
        var via: Result<ConnectionFeature, Failure>? = null,
        var candidate: Result<PlannedRoom, Failure>? = null,
        var fails: Int = 0
    )
}

abstract class Generator(
    protected val orchestrator: GenerationOrchestrator
) {
    abstract suspend fun run(root: PlannedRoom): Result<Unit, Failure>

    abstract fun satisfyConnection(
        room: PlannedRoom,
        via: ConnectionFeature,
        depth: Int,
        candidates: List<CandidateSolver.CandidateResult>
    ): Result<PlannedRoom, Failure>

    abstract fun chooseNextConnection(
        room: PlannedRoom,
        openConnections: Set<ConnectionFeature>
    ) : Result<ConnectionFeature, Failure>

    /**
     * @return random selection using the weight, null if the list is empty
     */
    fun weightedRandom(list: List<CandidateSolver.CandidateResult>) : CandidateSolver.CandidateResult? {
        if (list.isEmpty()) return null

        val totalWeight = list.sumOf { it.weight } / list.size
        val r = orchestrator.random.nextDouble() * totalWeight

        for (entry in list) {
            if (entry.weight >= r) {
                return entry
            }
        }

        throw NoSuchElementException()
    }

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

class GenerationDebug(
    val dungeon: Instance,
) {
    fun onIndex(room: PlannedRoom) {
        runBlocking {
            delay(20)
            room.blueprint.paste(dungeon, room.position, room.rotation)
        }
    }

    fun onUnindex(room: PlannedRoom) {
        runBlocking {
            delay(20)

            //room.blueprint.paste(dungeon, room.position, room.rotation, Block.AIR)

            val bounds = room.blueprint.boundsWith(room.position, room.rotation).expand(1.0).boundingBox
            for (x in bounds.min.x().roundToInt()..bounds.max.x().roundToInt())
                for (z in bounds.min.z().roundToInt()..bounds.max.z().roundToInt())
                    for (y in bounds.min.y().roundToInt()..bounds.max.y().roundToInt()) {
                        dungeon.setBlock(x, y, z, Block.AIR)
                    }

            for (chunk in bounds.containedChunks()) {
                val x = CoordConversion.chunkIndexGetX(chunk)
                val z = CoordConversion.chunkIndexGetZ(chunk)
                dungeon.getChunk(x, z)?.sendChunk()
            }
        }
    }
}

open class GenerationOrchestrator(
    val debug: GenerationDebug? = null,
    private val roomset: RoomSet,
    val random: Random,
    generatorSupplier: (GenerationOrchestrator)->Generator,
    var stats: Generator.Stats? = null,
    minY: Int? = -63,
    maxY: Int? = 319,
    val startPosition: BlockVec = BlockVec(0, 100, 0),
) {
    val candidateSolver = CandidateSolver(minY, maxY, stats)
    private val generator = generatorSupplier(this)

    lateinit var result: Tree<PlannedRoom>

    suspend fun generate(): Result<Tree<PlannedRoom>, Failure> {
        // Reuse existing stats object if provided to keep CandidateSolver/Generator references consistent
        if (stats == null) stats = Generator.Stats()
        candidateSolver.ensureReady()
        val start = TimeSource.Monotonic.markNow()
        val root = planRoot()

        result = Tree(root)
        commitRoot(root)

        val returned = generator.run(root)

        stats?.timeTaken = start.elapsedNow()
        return returned.mapSuccess { result }
    }

    fun reset() {
        val root = result.root.value
        revert(root)
        result = Tree(root)
        commitRoot(root)
        for (c in root.connections.allConnections) {
            root.connections.setOpen(c)
        }
    }

    // Expose minimal helpers to the Generator algorithm
    fun computeCandidates(
        owner: PlannedRoom,
        hostConnection: ConnectionFeature,
        hostPool: Pool
    ): List<CandidateSolver.CandidateResult> = candidateSolver.computeCandidates(owner, hostConnection, hostPool)

    private fun commitRoot(root: PlannedRoom) {
        // Index spatial region for root
        candidateSolver.spatialRegion.index(root.bounds)
        stats?.roomsPlanned++

        debug?.onIndex(root)
    }

    fun commit(parent: PlannedRoom, via: ConnectionFeature, newRoom: PlannedRoom) {
        val parentNode = result.getNode(parent) ?: error("Room '${parent.blueprint.id}' not in tree.")
        // Add child node to tree
        result.addNode(parentNode, newRoom)

        parent.connections.setClosed(via)
        newRoom.parentConnectsVia = via

        val matched = newRoom.connectedToParentVia
            ?: error("Child '${newRoom.blueprint.id}' did not specify connectedToParentVia")

        newRoom.connections.setClosed(matched)

        // Index spatial region for child
        candidateSolver.spatialRegion.index(newRoom.bounds)
        stats?.roomsPlanned++

        debug?.onIndex(newRoom)
    }

    fun revert(child: PlannedRoom) {
        // Before removal, reopen the connection on the parent that led to this child
        val node = result.getNode(child) ?: return
        val parentRoom = node.parent?.value
        val via = child.parentConnectsVia
        if (parentRoom != null && via != null) {
            parentRoom.connections.setOpen(via)
        }

        // Unindex the entire subtree and adjust planned rooms count
        val removedCount = unindexSubtree(node)
        stats?.roomsPlanned = (stats?.roomsPlanned ?: 0L) - removedCount

        // Remove from the tree (with subtree)
        result.removeNode(node)
    }

    private fun unindexSubtree(node: Tree<PlannedRoom>.Node): Long {
        var count = 0L
        fun dfs(n: Tree<PlannedRoom>.Node) {
            // Unindex children first (post-order just in case)
            for (c in n.children) dfs(c)
            candidateSolver.spatialRegion.unindex(n.value.bounds)
            count++

            debug?.onUnindex(n.value)
        }
        dfs(node)
        return count
    }

    protected fun planRoot() : PlannedRoom {
        val blueprint = roomset.rooms[roomset.rootRoomId]
            ?: error("No root room '${roomset.rootRoomId}' defined.")

        // Commiting the root room with any other rotation does not align the bounds correctly.
        val rotation = Rotation.CLOCKWISE_90
        val bounds = blueprint.boundsWith(startPosition, rotation)

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
    val bounds: Region = blueprint.boundsWith(position, rotation),
    val connectedToParentVia: ConnectionFeature? = null
) {
    val connections: Connections = Connections()
    var parentConnectsVia: ConnectionFeature? = null

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
        return "PlannedRoom(bounds=$bounds, rotation=$rotation, position=$position, blueprint=${blueprint.id}, connections=$connections"
    }

    inner class Connections {
        val allConnections = blueprint.featuresWith(rotation).connections
        // Only connections that are actually connectable (non-null pool with entries)
        private fun isConnectable(connection: ConnectionFeature): Boolean =
            connection.pool?.entries?.isNotEmpty() == true

        private var _openConnections = HashSet<ConnectionFeature>(allConnections.filter(::isConnectable))
        val openConnections: Set<ConnectionFeature> get() = _openConnections

        fun setOpen(connection: ConnectionFeature) {
            assertWithinRoom(connection)
            // Only track as open if it is a connectable connection
            if (isConnectable(connection)) {
                _openConnections += connection
            }
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