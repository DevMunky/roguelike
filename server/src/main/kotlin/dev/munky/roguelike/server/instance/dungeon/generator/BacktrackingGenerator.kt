package dev.munky.roguelike.server.instance.dungeon.generator

import dev.munky.roguelike.common.Result
import dev.munky.roguelike.common.handleFailure
import dev.munky.roguelike.common.toFailure
import dev.munky.roguelike.common.toSuccess
import dev.munky.roguelike.server.instance.dungeon.roomset.feature.ConnectionFeature
import kotlinx.coroutines.yield
import net.minestom.server.coordinate.BlockVec

class BacktrackingGenerator(orchestrator: GenerationOrchestrator) : Generator(orchestrator) {
    private var rootPosition = BlockVec(0)

    override suspend fun run(root: PlannedRoom): Result<Unit, Failure> {
        var state = State.PICK_CONNECTION
        val stack = ArrayDeque<Frame>()

        stack.addLast(Frame(root))
        rootPosition = root.position

        main@ while (stack.isNotEmpty()) {
            yield()
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
    ): Result<PlannedRoom, Failure> {
        val maxDepth = 25
        if (depth > maxDepth) return Failure.DEPTH_EXCEEDED.toFailure()
        val noTerminal = depth < maxDepth * .5
        val branchOut = depth < maxDepth * .8

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

        // TODO: Find out why layout/room/drop is NEVER chosen as the candidate, even with an exorbitantly high weight.
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