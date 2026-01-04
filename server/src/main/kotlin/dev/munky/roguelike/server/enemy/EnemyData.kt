package dev.munky.roguelike.server.enemy

import dev.munky.roguelike.server.EntityTypeSerializer
import dev.munky.roguelike.server.enemy.ai.behavior.AiBehavior
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.pathfinding.followers.FlyingNodeFollower
import net.minestom.server.entity.pathfinding.followers.GroundNodeFollower
import net.minestom.server.entity.pathfinding.followers.NodeFollower
import net.minestom.server.entity.pathfinding.generators.FlyingNodeGenerator
import net.minestom.server.entity.pathfinding.generators.GroundNodeGenerator
import net.minestom.server.entity.pathfinding.generators.NodeGenerator
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket

@Serializable
data class EnemyData(
    val id: String,
    val visual: EntityVisualType,
    val movement: EnemyMovementType,
    val behaviors: List<AiBehavior>
) {
    fun toEnemy(source: Enemy.Source) : Enemy = visual.createEntity(this, source)
}

enum class EnemyMovementType(val follower: (Entity)->NodeFollower, val generator: ()->NodeGenerator) {
    WALKING(::GroundNodeFollower, ::GroundNodeGenerator),
    FLYING(::FlyingNodeFollower, ::FlyingNodeGenerator)
}

@Serializable
sealed interface EntityVisualType {
    val entityType: EntityType

    fun createEntity(data: EnemyData, source: Enemy.Source) : Enemy

    @Serializable
    @SerialName("vanilla")
    data class Vanilla(
        @Serializable(with = EntityTypeSerializer::class)
        override val entityType: EntityType
    ) : EntityVisualType {
        override fun createEntity(data: EnemyData, source: Enemy.Source): Enemy = object: Enemy(data, source) {}
    }

    @Serializable
    @SerialName("player")
    data class Player(
        val username: String,
        val skinTexture: String? = null,
        val skinSignature: String? = null,
    ) : EntityVisualType {
        override val entityType: EntityType get() = EntityType.PLAYER
        override fun createEntity(data: EnemyData, source: Enemy.Source): Enemy = object: Enemy(data, source) {
            override fun updateNewViewer(player: net.minestom.server.entity.Player) {
                val properties = ArrayList<PlayerInfoUpdatePacket.Property>()
                if (skinTexture != null && skinSignature != null) {
                    properties.add(PlayerInfoUpdatePacket.Property("textures", skinTexture, skinSignature))
                }
                val entry = PlayerInfoUpdatePacket.Entry(
                    uuid, username, properties, false,
                    0, GameMode.SURVIVAL, null, null, 0, true
                )
                player.sendPacket(PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER, entry))

                // Spawn the player entity
                super.updateNewViewer(player)

                // Enable skin layers
                player.sendPackets(EntityMetaDataPacket(entityId,
                    mapOf(MetadataDef.Player.DISPLAYED_MODEL_PARTS_FLAGS.index() to Metadata.Byte((0.inv()).toByte()))
                ))
            }

            @Suppress("UnstableApiUsage")
            override fun updateOldViewer(player: net.minestom.server.entity.Player) {
                super.updateOldViewer(player)
                player.sendPacket(PlayerInfoRemovePacket(uuid))
            }
        }
    }

    @Serializable
    @SerialName("model")
    data class Model(val model: String) : EntityVisualType {
        override val entityType: EntityType get() = EntityType.INTERACTION
        override fun createEntity(data: EnemyData, source: Enemy.Source): Enemy = TODO("Model Implementation")
    }
}