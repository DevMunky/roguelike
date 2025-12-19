package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.enemy.ai.Ai
import dev.munky.roguelike.server.enemy.ai.behavior.ChaseTarget
import dev.munky.roguelike.server.enemy.ai.behavior.FindTarget
import dev.munky.roguelike.server.enemy.ai.behavior.MeleeAttackTarget
import dev.munky.roguelike.server.instance.RogueInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityPose
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage

/**
 * Players don't interact with enemies, enemies kill them.
 */
class Enemy(val data: EnemyData) : EntityCreature(data.visual.entityType) {
    val ai = Ai(this, CoroutineScope(Dispatchers.Default + SupervisorJob()))

    init {
        navigator.setNodeGenerator(data.movement.generator)
        navigator.setNodeFollower { data.movement.follower(this@Enemy) }

        for (behavior in data.behaviors) {
            ai.addBehavior(behavior)
        }

        getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.125
    }

    override fun spawn() {
        ai.start(instance as RogueInstance)
    }

    override fun despawn() {
        ai.stop()
    }

    override fun damage(damage: Damage): Boolean {
       (damage.source as? LivingEntity)?.let { ai.interrupt(Ai.ContextKey.TARGET, it) }
        return super.damage(damage)
    }

    override fun aiTick(time: Long) {}
}