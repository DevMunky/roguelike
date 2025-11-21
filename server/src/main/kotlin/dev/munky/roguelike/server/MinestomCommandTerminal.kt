package dev.munky.roguelike.server

import dev.munky.roguelike.common.console.Terminal
import dev.munky.roguelike.common.logger
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.ConsoleSender
import net.minestom.server.tag.TagHandler

class MinestomCommandTerminal : Terminal(), CommandSender {

    override fun parse(line: String) {
        MinecraftServer.getCommandManager().execute(this, line)
    }

    override fun sendMessage(message: Component) {
        LOGGER.info(message.asString())
    }

    override fun tagHandler(): TagHandler = TagHandler.newHandler()
    override fun identity(): Identity = Identity.nil()

    companion object {
        private val LOGGER = logger {}
    }
}