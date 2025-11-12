package dev.munky.roguelike.server

import dev.munky.modelrenderer.entity.ModelEntity
import dev.munky.modelrenderer.skeleton.Model
import kotlinx.coroutines.delay
import net.minestom.server.Auth
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import java.util.concurrent.CompletableFuture

suspend fun main() {
    RoguelikeServer.build {
        init(Auth.Online())
        start("localhost", 25565)
    }
    val instance = RoguelikeServer.server().process().instance().createInstanceContainer()

    val model = Model.decodeFromInputStream()
    val entity = TestModelEntity(model)
    while (RoguelikeServer.server().process().isAlive) delay(1000)
}

abstract class MinestomModel(val model: Model) : Entity(EntityType.TEXT_DISPLAY) {
    val modelEntity get() = ModelEntity(model)

    override fun setInstance(instance: Instance, spawnPosition: Pos): CompletableFuture<Void?>? {
        modelEntity.setLevel(RoguelikeServer.server().model().levelOf(instance.uuid))
        return super.setInstance(instance, spawnPosition)
    }

    override fun spawn() {
        modelEntity.spawn()
        super.spawn()
    }
}

class TestModelEntity(model: Model) : MinestomModel(model) {

}