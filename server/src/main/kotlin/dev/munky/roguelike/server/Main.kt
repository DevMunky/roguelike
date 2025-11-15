package dev.munky.roguelike.server

import dev.munky.modelrenderer.entity.ModelEntity
import dev.munky.modelrenderer.skeleton.Model
import kotlinx.coroutines.delay
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.chunk.ChunkSupplier
import org.joml.Vector3d
import java.io.File
import java.util.concurrent.CompletableFuture

suspend fun main() {
    Roguelike.build {
        init(Auth.Online())
    }
    val instance = Roguelike.server().process().instance().createInstanceContainer()
    val file = File("model/fott_byleth.bbmodel")
    val model = file.inputStream().buffered().use { Model.decodeFromInputStream(it) }
    val entity = TestModelEntity(model)

    instance.chunkSupplier = { i, x, z ->
        LightingChunk(i, x, z)
    }
    instance.setGenerator { unit ->
        unit.modifier().fillHeight(-64, 0, Block.GRASS_BLOCK)
    }

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) {
        it.player.respawnPoint = Pos(0.0, 5.0, 0.0)
        it.spawningInstance = instance
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) {
        it.player.isAllowFlying = true
    }

    entity.setInstance(instance, Pos(.0, 5.0, .0))

    Roguelike.server().start("localhost", 25565)
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