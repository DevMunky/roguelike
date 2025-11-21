package dev.munky.roguelike.server.command

fun helpCommand() = command("help") {
    executor { s, a ->
        s.sendMessage("<test>Welcome to Roguelike. This is help.")
    }
}