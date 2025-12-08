package dev.munky.roguelike.server.enemy

import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType

/**
 * Players don't interact with enemies, enemies kill them.
 */
data class Enemy(val data: EnemyData) : EntityCreature(EntityType.INTERACTION) {
    val visualEntity = data.visual.createEntity()


}