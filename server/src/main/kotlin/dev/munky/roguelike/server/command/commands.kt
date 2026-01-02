package dev.munky.roguelike.server.command

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.enemy.Enemy
import dev.munky.roguelike.server.enemy.Enemy.Source
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.Dungeon
import dev.munky.roguelike.server.instance.dungeon.ModifierSelectRenderer
import dev.munky.roguelike.server.item.DroppedItemRenderer
import dev.munky.roguelike.server.item.RogueItem
import dev.munky.roguelike.server.player.RoguePlayer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.coordinate.Pos
import kotlin.math.PI
import kotlin.random.Random

fun helpCommand() = command("help") {
    executor { s, a ->
        s.sendMessage("<test>Welcome to Roguelike. This is help.".asComponent())
    }
}

fun toggleDebug() = command("debug") {
    playerExecutor { s, _ ->
        val player = s as? RoguePlayer ?: return@playerExecutor
        player.isDebug = !player.isDebug
    }
}

fun testEnemy() = command("testEnemy") {
    playerExecutor{ s, _ ->
        val origin = s.position
        val data = Roguelike.server().enemies().toList().random()
        val radius = 20
        val halfRadius = radius / 2
        repeat(5) {
            val x = origin.x + (Random.nextDouble() * radius - halfRadius).toInt()
            val z = origin.z + (Random.nextDouble() * radius - halfRadius).toInt()
            val e = Enemy(data, Source.WhyNot)
            e.setInstance(s.instance, Pos(x, origin.y, z))
        }
    }
}

fun testModifierSelect() = command("testModifierSelect") {
    playerExecutor { s, _ ->
        val instance = s.instance as RogueInstance
        with(instance) {
            RenderDispatch.with(ModifierSelectRenderer)
                .with(RogueInstance, instance)
                .with(RoguePlayer, s as RoguePlayer)
                .with(ModifierSelectRenderer.ModifierSelection, Roguelike.server().modifiers().toList())
                .with(RenderKey.Position, s.position)
                .with(ModifierSelectRenderer.Width, PI / 2.0)
                .with(ModifierSelectRenderer.Radius, 2.0)
                .dispatchManaged()
        }
    }
}

fun stopCommand() = command("stop") {
    executor { s, _ ->
        MinecraftServer.LOGGER.info("Stopping server.")
        Runtime.getRuntime().exit(0)
    }
}

fun testDropItem() = command("testDropItem") {
    playerExecutor { s, _ ->
        val instance = s.instance as RogueInstance
        val player = s as RoguePlayer
        with(instance) {
            RenderDispatch.with(DroppedItemRenderer)
                .with(RogueInstance, instance)
                .with(RoguePlayer, player)
                .with(RogueItem, player.character.weapon)
                .with(RenderKey.Position, player.position.add(player.position.direction().mul(3.0)))
                .dispatchManaged()
        }
    }
}

fun testDungeon() = command("testDungeon") {
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