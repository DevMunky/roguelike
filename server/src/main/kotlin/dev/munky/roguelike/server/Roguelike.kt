package dev.munky.roguelike.server

import dev.munky.modelrenderer.ModelPlatform
import dev.munky.roguelike.server.command.helpCommand
import dev.munky.roguelike.server.instance.mainmenu.MainMenuInstance
import dev.munky.roguelike.server.player.AccountData
import dev.munky.roguelike.server.player.RoguelikePlayer
import dev.munky.roguelike.server.store.DynamicResourceStore
import dev.munky.roguelike.server.store.DynamicResourceStoreImpl
import kotlinx.serialization.json.Json
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerProcess
import java.nio.file.FileSystem
import kotlin.io.path.Path

class Roguelike private constructor() {
    lateinit var mc: MinecraftServer
    fun process() : ServerProcess = MinecraftServer.process()

    private val modelPlatform = ModelPlatform.register(MinestomModelPlatform())
    fun model() : ModelPlatform = modelPlatform

    private val terminal = MinestomCommandTerminal()
    fun terminal() : MinestomCommandTerminal = terminal

    private val accountStore = DynamicResourceStoreImpl(AccountData.serializer(), Json {
        prettyPrint = true
        encodeDefaults = true
    }, Path("accounts/"))
    fun accounts() : DynamicResourceStore<AccountData> = accountStore

    val mainMenu by lazy { MainMenuInstance.create() }

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
        MinecraftServer.getConnectionManager().setPlayerProvider(::RoguelikePlayer)
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
        const val NAMESPACE = "roguelike"
        private lateinit var INSTANCE: Roguelike
        fun server() = INSTANCE
        fun build(f: Roguelike.()->Unit) : Roguelike =
            if (::INSTANCE.isInitialized) error("There is already an instance of Roguelike.")
            else Roguelike().also(f).also(Roguelike::register)
    }
}