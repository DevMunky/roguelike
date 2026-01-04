package dev.munky.roguelike.server.enemy.ai

import dev.munky.roguelike.common.MutableTypedMap
import dev.munky.roguelike.common.TypedMap
import dev.munky.roguelike.common.logger
import dev.munky.roguelike.server.enemy.ai.behavior.AiBehavior
import dev.munky.roguelike.server.instance.RogueInstance
import kotlinx.coroutines.*
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.pathfinding.NavigableEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.cancellation.CancellationException

data class Ai<T>(
    val entity: T,
    val coroutineScope: CoroutineScope
) where T : LivingEntity, T: NavigableEntity {
    private val context = Context()
    private var stopped = false
    private val behaviors = CopyOnWriteArraySet<AiBehavior>()
    private var activeBehavior: AiBehavior? = null
    private var activeJob: Job? = null

    fun addBehavior(behavior: AiBehavior) { behaviors.add(behavior) }
    fun removeBehavior(behavior: AiBehavior) { behaviors.remove(behavior) }

    fun start(instance: RogueInstance) {
        stopped = false
        context[Context.Key.INSTANCE] = instance
        coroutineScope.launch {
            while (isActive && !entity.isDead && !stopped) {
                tick()
                delay(50)
            }
        }
    }

    fun stop() {
        stopped = true
        stopActiveBehavior()
    }

    fun tick() {
        var behavior: AiBehavior? = null
        var maxPriority = 0.0

        for (b in behaviors) {
            val priority = b.priority(context, entity)
            if (priority <= 0.0) continue
            if (priority > maxPriority) {
                maxPriority = priority
                behavior = b
            }
        }

        if (behavior == null) {
            LOGGER.debug("No behavior, idling.")
            stopActiveBehavior()
            return
        }

        if (activeBehavior != behavior)
            switchBehavior(behavior)
        else if (activeJob?.isCompleted == true)
            switchBehavior(behavior)
    }

    fun <T : Any> interrupt(key: Context.Key<T>, value: T?) {
        if (context[key] == value) return
        LOGGER.debug("Interrupt '{}' is now {}", key, value)
        stopActiveBehavior()
        value?.let { context[key] = it } ?: context.remove(key)
    }

    private fun stopActiveBehavior() {
        context.remove(Context.Key.TARGET)
        activeJob?.cancel("Stopping active behavior.")
        activeBehavior = null
    }

    private fun switchBehavior(newBehavior: AiBehavior) {
        activeJob?.cancel("Switch to new behavior '${newBehavior::class.simpleName}'.")
        activeBehavior = newBehavior

        activeJob = coroutineScope.launch {
            try {
                LOGGER.debug("Switch to new behavior '${newBehavior::class.simpleName}'")
                newBehavior.start(context, entity)
                // LOGGER.debug("Stopping active behavior, start() returned.")
                // stopActiveBehavior()
            } catch (e: CancellationException) {
                // Handled natively: The behavior was interrupted.
                // Any 'finally' blocks in the behavior will run for cleanup.
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    /**
     * A map of all the values in this context.
     */
    class Context : MutableTypedMap<Context> by MutableTypedMap.of(ConcurrentHashMap()) {
        @JvmInline
        value class Key<V>(private val id: String) : TypedMap.Key<Context, V> {
            companion object {
                val INSTANCE = Key<RogueInstance>("rogue_instance")
                val TARGET = Key<LivingEntity>("target")
            }
        }
    }

    companion object {
        private val LOGGER = logger {}
    }
}