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
import net.minestom.server.particle.Particle

/**
 * The figure that appears when you die.
 */
class Stylo : Enemy(EnemyData(
    "stylo",
    EntityVisualType.Player("Stylo", "", ""),
    EnemyMovementType.WALKING,
    listOf(
        StareDownTarget
    )
), Source.Stylo) {
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