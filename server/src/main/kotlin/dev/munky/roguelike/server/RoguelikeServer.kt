package dev.munky.roguelike.server

import dev.munky.modelrenderer.ModelRendererPlatform
import dev.munky.roguelike.server.command.helpCommand
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerProcess

class RoguelikeServer {
    lateinit var mc: MinecraftServer
    fun process() : ServerProcess = MinecraftServer.process()

    private val modelPlatform = ModelRendererPlatform.platform()
    fun model() : ModelRendererPlatform = modelPlatform

    private val terminal = MinestomCommandTerminal()
    fun terminal() : MinestomCommandTerminal = terminal

    fun renderDistance(r: Int) {
        requireNotTooLate()
        require(r > 1)
        System.setProperty("minestom.chunk-view-distance", "$r")
    }

    fun simulationDistance(r: Int) {
        requireNotTooLate()
        require(r > 1)
        System.setProperty("minestom.entity-view-distance", "$r")
    }

    fun targetTps(tps: Int) {
        requireNotTooLate()
        require(tps > 1)
        System.setProperty("minestom.tps", "$tps")
    }

    fun dispatchThreads(count: Int) {
        requireNotTooLate()
        require(count > 1)
        System.setProperty("minestom.dispatcher-threads", "$count")
    }

    /**
     * Cannot modify [net.minestom.server.ServerFlag] beyond this point.
     */
    fun init(auth: Auth) {
        mc = MinecraftServer.init(auth)
        MinecraftServer.setBrandName("roguelike")
        registerCommands()
    }

    fun start(host: String, port: Int) {
        mc.start(host, port)
    }

    fun register() {
        INSTANCE = this
    }

    private fun requireNotTooLate() = require(!::mc.isInitialized) { "Too late to modify server flags." }

    private fun registerCommands() {
        MinecraftServer.getCommandManager().register(helpCommand())
    }

    companion object {
        private lateinit var INSTANCE: RoguelikeServer
        fun server() = INSTANCE
        fun build(f: RoguelikeServer.()->Unit) : RoguelikeServer = RoguelikeServer().also { it.register() }.apply(f)
    }
}