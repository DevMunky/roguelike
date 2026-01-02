package dev.munky.roguelike.server.death

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.enemy.Enemy
import dev.munky.roguelike.server.enemy.EnemyData
import dev.munky.roguelike.server.enemy.EnemyMovementType
import dev.munky.roguelike.server.enemy.EntityVisualType
import dev.munky.roguelike.server.enemy.ai.behavior.StareDownTarget
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.raycast.Ray
import dev.munky.roguelike.server.util.ParticleUtil
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.MetadataDef
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.particle.Particle

/**
 * The figure that appears when you die.
 */
class Stylo : Enemy(EnemyData(
    "stylo",
    EntityVisualType.Vanilla(EntityType.PLAYER),
    EnemyMovementType.WALKING,
    listOf(
        StareDownTarget
    )
)) {
    val skinTexture = ""
    val skinSignature = ""
    val username = "Stylo"

    override fun updateNewViewer(player: Player) {
        val properties = listOf(
            PlayerInfoUpdatePacket.Property("textures", skinTexture, skinSignature)
        )

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

    override fun tick(time: Long) {
        super.tick(time)
        val instance = instance as? RogueInstance ?: return
        ParticleUtil.drawParticlesAround(instance, this, Particle.SQUID_INK, expansion = 1.2, amount = 20)
    }
}

object DeathRenderer : Renderer {
    override suspend fun RenderContext.render() {
        val player = require(RoguePlayer)
        val instance = require(RogueInstance)
        val stylo = Stylo()

        // find stylo's position
        val distance = 10.0
        val dir = player.position.direction()
        val ray = Ray(player.position, dir, distance)
        val block = ray.findBlocks(instance).nextClosest()
        val downRayOrigin = block?.point?.add(.0, 2.0, .0) ?: ray.origin.add(dir.mul(distance))
        val downRay = Ray(downRayOrigin, Vec(.0, -1.0, .0), distance)
        val finalBlocks = ray.findBlocks(instance).nextClosest()
        val finalPosition = finalBlocks?.point ?: downRay.origin.add(downRay.direction.mul(distance))

        stylo.setInstance(instance, finalPosition)

    }
}