package dev.munky.roguelike.server

import dev.munky.modelrenderer.ModelPlatform
import dev.munky.roguelike.server.command.helpCommand
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerProcess

class Roguelike {
    lateinit var mc: MinecraftServer
    fun process() : ServerProcess = MinecraftServer.process()

    private val modelPlatform = ModelPlatform.register(MinestomModelPlatform())
    fun model() : ModelPlatform = modelPlatform

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
        private lateinit var INSTANCE: Roguelike
        fun server() = INSTANCE
        fun build(f: Roguelike.()->Unit) : Roguelike = Roguelike().also { it.register() }.apply(f)
    }
}