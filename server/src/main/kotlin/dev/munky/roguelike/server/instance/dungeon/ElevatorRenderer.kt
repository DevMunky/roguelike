package dev.munky.roguelike.server.instance.dungeon

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.player.RoguelikePlayers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.audience.ForwardingAudience
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object ElevatorRenderer : Renderer {
    enum class RideState : RenderContext.Element {
        STARTING,
        DESCENDING,
        ASCENDING,
        COMPLETE,
        TO_BE_DISPOSED;

        override val key: RenderContext.Key<RideState> = Companion

        companion object : RenderContext.Key<RideState>
    }

    override suspend fun RenderContext.render() {
        val players = get(RoguelikePlayers) ?: listOf(require(RoguePlayer))
        val audience = ForwardingAudience { players }
        val instance = require(RogueInstance)

        for (player in players) {
            player.setInstance(instance, Pos.ZERO)
        }

        val timeUntilComplete = 10.seconds.toJavaDuration()
        val completeAt = Instant.now().plus(timeUntilComplete)

        val e1 = ElevatorEntity()
        e1.setInstance(instance, Pos.ZERO)
        e1.initialize(timeUntilComplete)
        val e2 = ElevatorEntity()
        e2.setInstance(instance, Pos.ZERO)
        e2.initialize(timeUntilComplete)
        val trackDistance = 10.0
        val nElevators = 2

        // State modifier
        launch {
            set(RideState, RideState.STARTING)

            while (isActive) {
                set(RideState, RideState.DESCENDING)
                delay(100)
            }

            set(RideState, RideState.COMPLETE)
        }

        // State machine
        watchAndRequire(RideState) {
            when (it) {
                RideState.STARTING -> {
                    audience.sendActionBar("Elevator starting.".asComponent())
                }
                RideState.DESCENDING, RideState.ASCENDING -> {
                    val timeLeft = Instant.now().until(completeAt)
                    audience.sendActionBar("$timeLeft until destination is reached.".asComponent())

                    val ratio = 1 - timeLeft.toMillis() / timeUntilComplete.toMillis().toDouble()
                    val trackPosition = (trackDistance * ratio) % nElevators
                    val step = trackDistance / nElevators
                    e1.teleport(e1.position.withY(trackPosition))
                    e2.teleport(e1.position.withY(step + trackPosition))
                }
                RideState.COMPLETE -> {
                    audience.sendActionBar("Destination reached.".asComponent())
                }
                RideState.TO_BE_DISPOSED -> dispose()
            }
        }

        onDispose {
            e1.remove()
        }
    }


    class ElevatorEntity : Entity(EntityType.ITEM_DISPLAY) {
        fun initialize(interpolation: Duration) {
            editEntityMeta(ItemDisplayMeta::class.java) {
                //it.posRotInterpolationDuration = (interpolation.toMillis() / 50).toInt()
                it.itemStack = ItemStack.of(Material.WHITE_CONCRETE)
            }
        }
    }
}