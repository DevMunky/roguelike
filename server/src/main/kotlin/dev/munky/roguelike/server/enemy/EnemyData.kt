package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.EntityTypeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.pathfinding.followers.FlyingNodeFollower
import net.minestom.server.entity.pathfinding.followers.GroundNodeFollower
import net.minestom.server.entity.pathfinding.followers.NodeFollower
import net.minestom.server.entity.pathfinding.generators.FlyingNodeGenerator
import net.minestom.server.entity.pathfinding.generators.GroundNodeGenerator
import net.minestom.server.entity.pathfinding.generators.NodeGenerator

@Serializable
data class EnemyData(
    val id: String,
    val visual: EntityVisualType,
    val movement: EnemyMovementType,
    // val behaviors: List<AiBehavior>
)

enum class EnemyMovementType(val follower: (Entity)->NodeFollower, val generator: ()->NodeGenerator) {
    WALKING(::GroundNodeFollower, ::GroundNodeGenerator),
    FLYING(::FlyingNodeFollower, ::FlyingNodeGenerator)
}

@Serializable
sealed interface EntityVisualType {
    val entityType: EntityType

    fun createEntity() : EntityCreature

    @Serializable
    @SerialName("vanilla")
    data class Vanilla(
        @Serializable(with = EntityTypeSerializer::class)
        override val entityType: EntityType
    ) : EntityVisualType {
        override fun createEntity(): EntityCreature = EntityCreature(entityType)
    }

    @SerialName("model")
    @Serializable
    data class Model(val model: String) : EntityVisualType {
        @Transient
        override val entityType: EntityType = EntityType.INTERACTION
        override fun createEntity(): EntityCreature = TODO("Model Implementation")
    }
}