package dev.munky.roguelike.server

import kotlinx.coroutines.delay
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer

suspend fun main() {
    RoguelikeServer.build {
        init(Auth.Online())
        start("localhost", 25565)
    }
    while (RoguelikeServer.server().process().isAlive) delay(1000)
}