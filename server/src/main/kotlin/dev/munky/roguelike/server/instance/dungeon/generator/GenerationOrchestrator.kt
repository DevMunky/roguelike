package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.Result
import dev.munky.roguelike.common.mapSuccess
import dev.munky.roguelike.server.instance.dungeon.roomset.Pool
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.BlockVec
import java.util.Random
import kotlin.time.TimeSource

/**
 * The orchestrator is responsible for managing the state of the generation process
 * and delegating to the [generator implementation][Generator].
 *
 * @param debug some debugging utilities that require additional context
 * not necessary for normal execution, so it is delegated to another object.
 * @param roomset the RoomSet to use for rooms and pools of rooms.
 * @param generatorSupplier the supplier for the generator implementation to use.
 * @param doStats If `false`, stats will not be recorded, and the calculations needed for them will be skipped.
 *
 * @see BacktrackingGenerator
 * @see dev.munky.roguelike.server.instance.dungeon.Dungeon
 */
open class GenerationOrchestrator(
    roomset: RoomSet,
    generatorSupplier: (GenerationOrchestrator)->Generator,
    val debug: GenerationDebug? = null,
    val random: Random = Random(),
    doStats: Boolean = false,
    minY: Int? = -63,
    maxY: Int? = 319,
    startPosition: BlockVec = BlockVec(0, 100, 0),
) {
    private val generator = generatorSupplier(this)
    private lateinit var result: Tree<PlannedRoom>

    /**
     * Creating the root room with any other rotation does not align the bounds correctly with the blocks in-world.
     * Could fix the bug, but I would rather just lock the rotation. No one will notice.
     */
    private var rootRoom: PlannedRoom = PlannedRoom(
        blueprint = roomset.rooms[roomset.rootRoomId]
            ?: error("No root room '${roomset.rootRoomId}' defined."),
        position = startPosition,
        rotation = Rotation.CLOCKWISE_90,
    )

    /**
     * The generation stats for the last [generate] invocation.
     * Will always be null if [doStats] was false.
     */
    var stats: Generator.Stats? = if (doStats) Generator.Stats() else null
        private set
    val candidateSolver = CandidateSolver(minY, maxY)

    /**
     * Generate a tree of [PlannedRoom] for use in a [dev.munky.roguelike.server.instance.dungeon.Dungeon], or - rarely - return a [Generator.Failure].
     *
     * It is ultimately up to the [Generator] implementation to decide when a failure occurs,
     * as well as when a failure is returned to the caller.
     */
    suspend fun generate(): Result<Tree<PlannedRoom>, Generator.Failure> {
        val start = TimeSource.Monotonic.markNow()
        if (stats != null) stats = Generator.Stats()
        candidateSolver.ensureReady(stats)

        commitRoot()

        val returned = generator.run(rootRoom)

        stats?.timeTaken = start.elapsedNow()
        return returned.mapSuccess { result }
    }

    fun reset() {
        // create new root room to guarantee
        // there is no leftover state
        rootRoom = rootRoom.copy(connectedToParentVia = null)
        revert(rootRoom)

        commitRoot()
    }

    // Expose minimal helpers to the Generator implementation
    fun computeCandidates(
        owner: PlannedRoom,
        hostConnection: ConnectionFeature,
        hostPool: Pool
    ): List<CandidateSolver.CandidateResult> = candidateSolver.computeCandidates(owner, hostConnection, hostPool)

    private fun commitRoot() {
        // Index spatial region for root
        candidateSolver.spatialRegion.index(rootRoom.bounds)
        stats?.roomsPlanned++
        stats?.descends++
        result = Tree(rootRoom, ::HashMap)

        debug?.onIndex(rootRoom)
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
        stats?.descends++

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
        stats?.ascends += removedCount

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
}