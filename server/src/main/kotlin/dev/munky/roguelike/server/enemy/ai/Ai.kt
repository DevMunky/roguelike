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
            stopActiveBehavior()
            return
        }

        if (activeBehavior != behavior)
            switchBehavior(behavior)
        else if (activeJob?.isCompleted == true)
            switchBehavior(behavior)
    }

    fun <T : Any> interrupt(key: Context.Key<T>, value: T) {
        stopActiveBehavior()
        context[key] = value
    }

    private fun stopActiveBehavior() {
        context.remove(Context.Key.TARGET)
        activeJob?.cancel("Stopping active behavior.")
        activeBehavior = null
    }

    private fun switchBehavior(newBehavior: AiBehavior) {
        activeJob?.cancel("Switching to new behavior ${newBehavior::class.simpleName}.")
        activeBehavior = newBehavior

        LOGGER.debug("Switching to behavior '${newBehavior::class.simpleName}'")

        activeJob = coroutineScope.launch {
            try {
                newBehavior.start(context, entity)
            } catch (e: CancellationException) {
                // Handled natively: The behavior was interrupted.
                // Any 'finally' blocks in the behavior will run for cleanup.
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Context {
        private val data = ConcurrentHashMap<Key<*>, Any>()

        operator fun <T : Any> get(key: Key<T>) = data[key] as T?
        operator fun <T : Any> contains(key: Key<T>) = data.containsKey(key)
        operator fun <T : Any> set(key: Key<T>, value: T) = data.put(key, value)
        fun <T : Any> remove(key: Key<T>) : T? = data.remove(key) as T?

        @Suppress("unused")
        data class Key<T : Any>(val id: String) {
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