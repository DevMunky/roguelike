package dev.munky.roguelike.server

import dev.munky.roguelike.common.console.Terminal
import net.kyori.adventure.identity.Identity
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.tag.TagHandler

class MinestomCommandTerminal : Terminal(), CommandSender {
    override fun parse(line: String) {
        @Suppress("UnstableApiUsage")
        MinecraftServer.getCommandManager().parseCommand(this, line)
    }

    override fun tagHandler(): TagHandler = TagHandler.newHandler()
    override fun identity(): Identity = Identity.nil()
}