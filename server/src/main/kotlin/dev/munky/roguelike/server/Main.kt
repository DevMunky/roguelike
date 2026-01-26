package dev.munky.roguelike.server

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import com.sun.management.OperatingSystemMXBean
import com.sun.management.ThreadMXBean
import dev.munky.modelrenderer.entity.ModelEntity
import dev.munky.modelrenderer.skeleton.Model
import dev.munky.roguelike.common.launch
import dev.munky.roguelike.server.instance.mainmenu.MainMenuInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.kyori.adventure.bossbar.BossBar
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
import org.joml.Vector3d
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.CompletableFuture

suspend fun main(vararg args: String) {
    val prop = Properties()
    val propFile = File("server.properties")
    if (propFile.exists()) propFile.inputStream().use {
        prop.load(it)
    }

    val xml = Roguelike::class.java.getResource("logback.xml")
    if (xml != null) loadLogbackConfig(xml.toExternalForm())

    Roguelike.build {
        dispatchThreads(3)
        val favIs = javaClass.getResourceAsStream("/favicon.png") ?: error("No favicon found")
        favIs.use {
            favicon(it)
        }
        description("<green>Roguelike".asComponent())
        init(Auth.Online())
    }

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) {
        it.player.respawnPoint = Pos(0.0, .1, 0.0)
        it.spawningInstance = MainMenuInstance.create()
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) {
        it.player.isAllowFlying = true
    }

    var i = 0L
    val bossBar = BossBar.bossBar("".asComponent(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
    var bytesPerSecond = .0
    Dispatchers.Default.launch {
        val mxBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        mxBean.isThreadAllocatedMemoryEnabled = true

        var lastBytes = 0L
        var lastTime = System.nanoTime()

        while (isActive) {
            delay(100)

            var nowBytes = 0L
            for (id in mxBean.allThreadIds) {
                val threadBytes = mxBean.getThreadAllocatedBytes(id)
                if (threadBytes == -1L) continue
                nowBytes += threadBytes
            }

            val nowTime = System.nanoTime()
            val deltaSeconds = (nowTime - lastTime) / 1_000_000_000.0
            val deltaBytes = nowBytes - lastBytes
            bytesPerSecond = deltaBytes / deltaSeconds

            lastTime = nowTime
            lastBytes = nowBytes
        }
    }

    MinecraftServer.getGlobalEventHandler().addListener(ServerTickMonitorEvent::class.java) {
        if (++i % 5 != 0L) return@addListener
        val mspt = String.format("%.2f", it.tickMonitor.tickTime)
        val ram = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / 1_000_000.0
        val cpu = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).processCpuLoad
        val mbps = bytesPerSecond / 1024 / 1024
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            bossBar.name("<white>MB/s = ${String.format("%.2f", mbps)}, CPU = ${String.format("%.2f%%", cpu*100)}, MSPT = $mspt, RAM = ${String.format("%.2f", ram)}MB".asComponent())
            bossBar.progress((it.tickMonitor.tickTime.toFloat() / 50f).coerceIn(0f, 1f))
            player.showBossBar(bossBar)
        }
    }

    Roguelike.server().start(prop.getProperty("server-ip") ?: "localhost", prop.getProperty("server-port")?.toInt() ?: 25565)

    while (Roguelike.server().process().isAlive) delay(1000)
}

fun loadLogbackConfig(path: String) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    context.reset() // wipe previous config

    val configurator = JoranConfigurator()
    configurator.setContext(context)

    try {
        configurator.doConfigure(path) // path = file or classpath resource
    } catch (e: JoranException) {
        throw RuntimeException("Failed to load Logback config: " + path, e)
    }
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