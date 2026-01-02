package dev.munky.roguelike.server.util

import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.randomPerpendicular
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import kotlin.random.Random

object ParticleUtil {
    fun drawParticlesAround(instance: RogueInstance, position: Point, bb: BoundingBox, particle: Particle, expansion: Double = 1.05, amount: Int = 1, speed: Float = 0f) {
        val xz = bb.width() * expansion
        val y = bb.height() * expansion
        val particles = ArrayList<ParticlePacket>()
        repeat(amount) {
            particles.add(ParticlePacket(
                particle,
                position,
                Vec(xz, y, xz),
                speed,
                1
            ))
        }
        for (player in instance.players) {
            player.sendPackets(particles as List<ParticlePacket>)
        }
    }

    fun drawParticlesAround(instance: RogueInstance, entity: Entity, particle: Particle, expansion: Double = 1.05, amount: Int = 1, speed: Float = 0f) {
        drawParticlesAround(instance, entity.position, entity.boundingBox, particle, expansion, amount, speed)
    }
    fun drawLine(
        p1: Point,
        p2: Point,
        particle: Particle,
        iterations: Int
    ) : List<ParticlePacket> {
        var points = ArrayList<Point>()
        points.add(p1)
        points.add(p2)

        repeat(iterations) {
            val next = ArrayList<Point>()

            for (i in 0..<points.size - 1) {
                val a = points[i]
                val b = points[i + 1]

                val midpoint = a.add(b).mul(0.5)

                next.add(a)
                next.add(midpoint)
            }

            next.add(points.last())
            points = next
        }

        return points.map {
            ParticlePacket(
                particle,
                it.x(), it.y(), it.z(),
                0f, 0f, 0f, 0f, 1
            )
        }
    }
    fun drawLightningArc(
        p1: Point,
        p2: Point,
        particle: Particle,
        iterations: Int,
        initialDisplacement: Double,
        rng: Random
    ) : List<ParticlePacket> {
        var points = ArrayList<Point>()
        points.add(p1)
        points.add(p2)

        var displacement = initialDisplacement

        repeat(iterations) {
            val next = ArrayList<Point>()

            for (i in 0..<points.size - 1) {
                val a = points[i]
                val b = points[i + 1]

                var midpoint = a.add(b).mul(0.5)
                val dir = b.sub(a).asVec().normalize()
                val perpendicular = dir.randomPerpendicular(rng)

                val offset: Double = (rng.nextDouble() * 2.0 - 1.0) * displacement
                midpoint = midpoint.add(perpendicular.mul(offset))

                next.add(a)
                next.add(midpoint)
            }

            next.add(points[points.size - 1])
            points = next

            displacement *= 0.5 // decay for finer details (so it isn't just a cylinder of noise)
        }

        return points.map {
            ParticlePacket(
                particle,
                it.x(), it.y(), it.z(),
                0f, 0f, 0f, 0f, 1
            )
        }
    }
}