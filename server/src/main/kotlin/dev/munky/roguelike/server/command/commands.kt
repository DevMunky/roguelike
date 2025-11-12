package dev.munky.roguelike.server.command

import dev.munky.tenebris.core.command.command

fun helpCommand() = command("help") {
    executor { s, a ->
        s.sendMessage("<test>Welcome to Roguelike. This is help.")
    }
}