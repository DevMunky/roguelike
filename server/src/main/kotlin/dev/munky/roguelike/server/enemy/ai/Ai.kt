package dev.munky.roguelike.server.enemy.ai

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
        context[ContextKey.INSTANCE] = instance
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

    fun <T : Any> interrupt(key: ContextKey<T>, value: T?) {
        if (context[key] == value) return
        LOGGER.debug("Interrupt '{}' is now {}", key, value)
        stopActiveBehavior()
        value?.let { context.set(key, it) } ?: context.remove(key)
    }

    private fun stopActiveBehavior() {
        context.remove(ContextKey.TARGET)
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
            } catch (e: CancellationException) {
                // Handled natively: The behavior was interrupted.
                // Any 'finally' blocks in the behavior will run for cleanup.
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inner class Context {
        private val data = ConcurrentHashMap<ContextKey<*>, Any>()

        /**
         * Get the value associated with a key or null if it doesn't exist.
         */
        operator fun <T : Any> get(key: ContextKey<T>) = data[key] as T?

        /**
         * Check if the context currently contains a value for a key.
         */
        operator fun <T : Any> contains(key: ContextKey<T>) = data.containsKey(key)

        /**
         * Sets a value in the context without interrupting.
         */
        operator fun <T : Any> set(key: ContextKey<T>, value: T) = data.put(key, value)

        /**
         * Removes a value from the context without interrupting.
         */
        fun <T : Any> remove(key: ContextKey<T>) : T? = data.remove(key) as T?

        /**
         * Interrupt the Ai associated with this context.
         */
        fun <T : Any> interrupt(key: ContextKey<T>, value: T?) = this@Ai.interrupt(key, value)
    }

    @Suppress("unused")
    @JvmInline
    value class ContextKey<T : Any> private constructor (private val id: String) {
        companion object {
            val INSTANCE = ContextKey<RogueInstance>("rogue_instance")
            val TARGET = ContextKey<LivingEntity>("target")
        }
    }

    companion object {
        private val LOGGER = logger {}
    }
}