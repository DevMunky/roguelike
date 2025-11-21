package dev.munky.roguelike.server

import dev.munky.modelrenderer.entity.ModelEntity
import dev.munky.modelrenderer.skeleton.Model
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.instance.mainmenu.MainMenuInstance
import dev.munky.roguelike.server.instance.mainmenu.MainMenuRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import org.joml.Vector3d
import java.io.File
import java.util.Properties
import java.util.concurrent.CompletableFuture

suspend fun main(vararg args: String) {
    val prop = Properties()
    val propFile = File("server.properties")
    if (propFile.exists()) propFile.inputStream().use {
        prop.load(it)
    }
    Roguelike.build {
        init(Auth.Online())
    }

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) {
        it.player.respawnPoint = Pos(0.0, -44.5, 0.0)
        it.spawningInstance = MainMenuInstance.create()
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) {
        it.player.isAllowFlying = true
        if (it.player.instance is MainMenuInstance) RenderDispatch
            .with(MainMenuRenderer)
            .with(RenderKey.Player, it.player)
            .dispatch()
    }

    MinecraftServer.getGlobalEventHandler().addListener(ServerTickMonitorEvent::class.java) {
        val mspt = String.format("%.2f", it.tickMonitor.tickTime)
        val ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000000.0
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            player.sendActionBar("MSPT = $mspt, RAM = ${String.format("%.2f", ram)}MB".asComponent())
        }
    }

    Roguelike.server().start(prop.getProperty("server-ip") ?: "localhost", prop.getProperty("server-port")?.toInt() ?: 25565)

    while (Roguelike.server().process().isAlive) delay(1000)
}

abstract class MinestomModelEntity(val model: Model) : Entity(EntityType.TEXT_DISPLAY) {
    val modelEntity = ModelEntity(model)

    override fun setInstance(instance: Instance, spawnPosition: Pos): CompletableFuture<Void?>? {
        modelEntity.level = Roguelike.server().model().levelOf(instance.uuid)
        return super.setInstance(instance, spawnPosition)//.whenComplete { _, _ -> spawn() }
    }

    override fun spawn() {
        modelEntity.spawn()
        super.spawn()
    }

    override fun tick(time: Long) {
        if (modelEntity.rootEntity != null ) velocity = Vec(0.05, .0, .0)
        super.tick(time)
    }

    @Suppress("UnstableApiUsage")
    override fun refreshPosition(newPosition: Pos, ignoreView: Boolean, sendPackets: Boolean) {
        modelEntity.position = Vector3d(newPosition.x(), newPosition.y(), newPosition.z())
        super.refreshPosition(newPosition, ignoreView, sendPackets)
    }
}

class TestModelEntity(model: Model) : MinestomModelEntity(model) {

}