package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ai.EntityAIGroupBuilder
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.attribute.AttributeInstance
import net.minestom.server.entity.damage.Damage
import java.time.Duration

/**
 * Players don't interact with enemies, enemies kill them.
 */
class Enemy(val data: EnemyData) : EntityCreature(data.visual.entityType) {
    init {
        navigator.setNodeGenerator(data.movement.generator)
        navigator.setNodeFollower { data.movement.follower(this@Enemy) }

        addAIGroup(EntityAIGroupBuilder()
            .addGoalSelector(MeleeAttackGoal(this, 1.0, Duration.ofMillis(500)))
            .addTargetSelector(ClosestEntityTarget(this, 30.0) { target is RoguePlayer })
            .build())
    }
}