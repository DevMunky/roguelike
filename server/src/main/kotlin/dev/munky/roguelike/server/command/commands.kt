package dev.munky.roguelike.server.command

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.dungeon.Dungeon
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
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

fun testDungeon() = command("testdungeon") {
    playerExecutor{ s, _ ->
        s.sendMessage("testing dungeon")
        val ctx = Dispatchers.Default + CoroutineExceptionHandler { _, t ->
            s.sendMessage("<red>Exception caught: $t".asComponent())
            t.printStackTrace()
        }
        ctx.launch {
            val roomset = Roguelike.server().roomSets()["test"]!!
            val dungeon = Dungeon.create(roomset)
            s.setInstance(dungeon, Pos(.0, 40.0, .0))
        }
    }
}