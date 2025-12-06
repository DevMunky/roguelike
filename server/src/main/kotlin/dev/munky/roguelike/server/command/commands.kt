package dev.munky.roguelike.server.command

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.instance.dungeon.Dungeon
import dev.munky.roguelike.server.player.RoguePlayer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.arguments.ArgumentType
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
    add(ArgumentType.String("roomset_id")) {
        playerExecutor{ s, c ->
            s.sendMessage("testing dungeon")
            val roomsetId = c.get<String>("roomset_id")
            val ctx = Dispatchers.Default + CoroutineExceptionHandler { _, t ->
                s.sendMessage("<red>Exception caught: $t".asComponent())
                t.printStackTrace()
            }
            val roomset = Roguelike.server().roomSets()[roomsetId]!!
            ctx.launch {
                val dungeon = Dungeon.create(roomset, listOf(s as RoguePlayer))
            }
        }
    }
}