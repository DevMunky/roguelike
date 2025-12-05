package dev.munky.roguelike.server

import dev.munky.modelrenderer.ModelPlatform
import dev.munky.roguelike.server.command.helpCommand
import dev.munky.roguelike.server.command.spawnRandoms
import dev.munky.roguelike.server.command.testDungeon
import dev.munky.roguelike.server.instance.InstanceManager
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSet
import dev.munky.roguelike.server.instance.dungeon.roomset.RoomSetData
import dev.munky.roguelike.server.instance.mainmenu.MainMenuInstance.Companion.MENU_DIMENSION
import dev.munky.roguelike.server.interact.Interactable
import dev.munky.roguelike.server.item.RogueItem
import dev.munky.roguelike.server.item.modifier.ModifierData
import dev.munky.roguelike.server.player.AccountData
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.store.DynamicResourceStore
import dev.munky.roguelike.server.store.DynamicResourceStoreImpl
import dev.munky.roguelike.server.store.ResourceStore
import dev.munky.roguelike.server.store.ResourceStoreImpl
import dev.munky.roguelike.server.store.TransformingResourceStoreImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import net.benwoodworth.knbt.Nbt
import net.benwoodworth.knbt.NbtCompression
import net.benwoodworth.knbt.NbtVariant
import net.hollowcube.schem.Structure
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerProcess
import kotlin.io.path.Path

class Roguelike private constructor() {
    private lateinit var mc: MinecraftServer
    fun process() : ServerProcess = MinecraftServer.process()

    private val modelPlatform = ModelPlatform.register(MinestomModelPlatform())
    fun model() : ModelPlatform = modelPlatform

    private val terminal = MinestomCommandTerminal()
    fun terminal() : MinestomCommandTerminal = terminal

    private val instanceManager = InstanceManager()
    fun instanceManager() = instanceManager

    private val accountStore = DynamicResourceStoreImpl(
        AccountData.serializer(),
        Json {
            prettyPrint = true
            namingStrategy = JsonNamingStrategy.SnakeCase
            encodeDefaults = true
        },
        Path("account/"),
        key = { uuid.toString() }
    )
    fun accounts() : DynamicResourceStore<AccountData> = accountStore

    private val modifierStore = DynamicResourceStoreImpl(
        ModifierData.serializer(),
        Json {
            prettyPrint = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        },
        Path("modifier/"),
        key = { id }
    )

    /**
     * Contains all default Modifiers.
     */
    fun modifiers() : DynamicResourceStore<ModifierData> = modifierStore

    private val roomSetStore = TransformingResourceStoreImpl(
        RoomSetData.serializer(),
        Json {
            prettyPrint = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        },
        Path("roomset/"),
        transform = { id to RoomSet.create(this) }
    )
    fun roomSets() : ResourceStore<RoomSet> = roomSetStore

    private val structureStore = ResourceStoreImpl(
        StructureSerializer,
        Nbt {
            variant = NbtVariant.Java
            nameRootClasses = false
            compression = NbtCompression.Gzip
        },
        directory = Path("structure/"),
        key = { it }
    )
    fun structures() : ResourceStore<Structure> = structureStore

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
        ourInit()
        minecraftInit()
    }

    fun ourInit() = runBlocking {
        Interactable.initialize()
        RogueItem.initialize()
        RogueInstance.initialize()
        withContext(Dispatchers.IO) {
            accounts().load()
            modifiers().load()
            structures().load()
            roomSets().load()
        }
    }

    fun minecraftInit() {
        MinecraftServer.setBrandName("roguelike")
        MinecraftServer.getConnectionManager().setPlayerProvider(::RoguePlayer)
        MinecraftServer.getDimensionTypeRegistry().register("${NAMESPACE}:main_menu", MENU_DIMENSION)

        registerCommands()
        registerBlockHandlers()
    }

    fun start(host: String, port: Int) {
        if (!::mc.isInitialized) error("Call Roguelike.init() before starting the server.")
        mc.start(host, port)
    }

    fun register() {
        INSTANCE = this
    }

    private fun requireNotTooLate() = require(!::mc.isInitialized) { "Too late to modify server flags." }

    private fun registerCommands() {
        MinecraftServer.getCommandManager().unknownCommandCallback = { s, c ->
            s.sendMessage("Unknown command '$c'.".asComponent())
        }
        MinecraftServer.getCommandManager().register(helpCommand())
        MinecraftServer.getCommandManager().register(spawnRandoms())
        MinecraftServer.getCommandManager().register(testDungeon())
    }

    companion object {
        const val NAMESPACE = "roguelike"
        private lateinit var INSTANCE: Roguelike
        fun server() = INSTANCE
        fun build(f: Roguelike.()->Unit) : Roguelike =
            if (::INSTANCE.isInitialized) error("There is already an instance of Roguelike.")
            else Roguelike().also(Roguelike::register).also(f)
    }
}