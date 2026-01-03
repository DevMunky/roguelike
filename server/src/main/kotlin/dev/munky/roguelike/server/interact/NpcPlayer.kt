package dev.munky.roguelike.server.interact

import net.minestom.server.entity.*
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket

class FakePlayer(
    val username: String,
    val skinTexture: String? = null,
    val skinSignature: String? = null
) : LivingEntity(EntityType.PLAYER) {
    override fun updateNewViewer(player: Player) {
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
    override fun updateOldViewer(player: Player) {
        super.updateOldViewer(player)
        player.sendPacket(PlayerInfoRemovePacket(uuid))
    }
}

/**
 * A fake player.
 */
abstract class NpcPlayer(
    val username: String,
    val skinTexture: String? = null,
    val skinSignature: String? = null
) : TalkingInteractableCreature(EntityType.PLAYER) {
    override fun updateNewViewer(player: Player) {
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
    override fun updateOldViewer(player: Player) {
        super.updateOldViewer(player)
        player.sendPacket(PlayerInfoRemovePacket(uuid))
    }
}