package dev.munky.roguelike.server.enemy.ai.behavior

import dev.munky.roguelike.server.enemy.ai.Ai
import kotlinx.serialization.Serializable
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.pathfinding.NavigableEntity

@Serializable
sealed interface AiBehavior {
    fun <T> priority(context: Ai<T>.Context, entity: T) : Double where T : LivingEntity, T: NavigableEntity

    suspend fun <T> start(context: Ai<T>.Context, entity: T) where T : LivingEntity, T: NavigableEntity
}