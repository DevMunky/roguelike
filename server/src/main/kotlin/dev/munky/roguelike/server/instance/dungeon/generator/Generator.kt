package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.Result
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
            if (entry.blueprint.id == "layout/room/drop")
                return entry
            if (entry.weight >= r)
                return entry
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
        var spatialBinMaxSize: Long = 0L,

        var heightBoundsFails: Long = 0L,
        var timeTaken: Duration = 0.milliseconds,
    )
}

/**
 * Intentionally blocks (un)indexing of rooms for 20 ms + the time to
 * paste/unpaste the room to slow down generation for debugging.
 */
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
            room.blueprint.paste(dungeon, room.position, room.rotation, Block.AIR)
        }
    }
}