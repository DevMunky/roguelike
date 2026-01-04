package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.enemy.ai.Ai
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.Dungeon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.instance.Instance
import java.util.concurrent.CompletableFuture

/**
 * Players don't interact with enemies, enemies kill them.
 */
abstract class Enemy(val data: EnemyData, val source: Source) : EntityCreature(data.visual.entityType) {
    val ai = Ai(this, CoroutineScope(Dispatchers.Default + SupervisorJob()))

    init {
        navigator.setNodeGenerator(data.movement.generator)
        navigator.setNodeFollower { data.movement.follower(this@Enemy) }

        for (behavior in data.behaviors) {
            ai.addBehavior(behavior)
        }

        getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.125
    }

    override fun setInstance(instance: Instance?, spawnPosition: Pos?): CompletableFuture<Void?>? =
        super.setInstance(instance, spawnPosition).also { if (instance is RogueInstance) ai.start(instance) }

    override fun remove(permanent: Boolean) {
        ai.stop()
        super.remove(permanent)
    }

    override fun damage(damage: Damage): Boolean {
        (damage.source as? LivingEntity)?.let {
            ai.interrupt(Ai.Context.Key.TARGET, it)
        }
        return super.damage(damage)
    }

    /**
     * Don't tick vanilla ai.
     */
    override fun aiTick(time: Long) {}

    sealed interface Source {
        data class DungeonRoom(val room: Dungeon.Room) : Source
        data object Stylo : Source
        data object WhyNot : Source
    }
}