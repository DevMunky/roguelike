package dev.munky.roguelike.server.command

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import kotlin.random.Random

fun helpCommand() = command("help") {
    executor { s, a ->
        s.sendMessage("<test>Welcome to Roguelike. This is help.")
    }
}

fun spawnRandoms() = command("spawnrandoms") {
    playerExecutor{ s, _ ->
        val origin = s.position
        repeat(10) {
            val x = origin.x + (Random.nextDouble() * 10 - 5).toInt()
            val z = origin.z + (Random.nextDouble() * 10 - 5).toInt()
            val e = EntityCreature(EntityType.ZOMBIE)
            e.setInstance(s.instance, Pos(x, origin.y, z))
        }
    }
}