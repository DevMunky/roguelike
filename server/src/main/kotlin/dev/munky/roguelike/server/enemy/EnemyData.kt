package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.EntityTypeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType

@Serializable
data class EnemyData(
    val id: String,
    val visual: EntityVisualType,
)

@Serializable
sealed interface EntityVisualType {
    val type: EntityType

    fun createEntity() : EntityCreature

    @Serializable
    data class Vanilla(
        @Serializable(with = EntityTypeSerializer::class)
        override val type: EntityType
    ) : EntityVisualType {
        override fun createEntity(): EntityCreature = EntityCreature(type)
    }

    @Serializable
    data class Modeled(val model: String) : EntityVisualType {
        @Transient
        override val type: EntityType = EntityType.INTERACTION
        override fun createEntity(): EntityCreature = TODO("Model Implementation")
    }
}