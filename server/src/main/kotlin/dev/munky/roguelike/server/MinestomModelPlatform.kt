package dev.munky.roguelike.server

import dev.munky.modelrenderer.ModelPlatform
import kotlinx.coroutines.future.asDeferred
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.UUID

class MinestomModelPlatform : ModelPlatform {
    override fun levelOf(uuid: UUID): ModelPlatform.Level {
        return MinestomLevel(MinecraftServer.getInstanceManager().getInstance(uuid)!!)
    }

    class MinestomLevel(val instance: Instance) : ModelPlatform.Level {
        override fun playSound(at: Vector3d, soundId: String) {
            val sound = Sound.sound(Key.key(soundId), Sound.Source.MASTER, 1f, 1f)
            instance.playSound(sound, at.x, at.y, at.z)
        }

        override suspend fun spawnInteraction() {
            TODO("Not yet implemented")
        }

        override suspend fun spawnItemDisplay(
            x: Double,
            y: Double,
            z: Double,
            model: String
        ): ModelPlatform.ItemDisplayEntity {
            val entity = Entity(EntityType.ITEM_DISPLAY)
            val spawn = Pos(x, y, z)
            instance.loadChunk(spawn).asDeferred().join()
            entity.setInstance(instance, spawn)
            entity.setNoGravity(true)
            entity.editEntityMeta(ItemDisplayMeta::class.java) {
                it.itemStack = ItemStack.of(Material.fromKey("minecraft:black_wool"))
            }
            return MinestomItemDisplayEntity(entity, model)
        }
    }

    class MinestomItemDisplayEntity(val entity: Entity, val resource: String) : ModelPlatform.ItemDisplayEntity {
        val debugName = Entity(EntityType.TEXT_DISPLAY)
        init {
            debugName.setInstance(entity.instance!!, entity.position)
            debugName.editEntityMeta(TextDisplayMeta::class.java) {
                it.scale = Vec(0.1, 0.1, 0.1)
                it.text = resource.asComponent()
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
            }
            debugName.setNoGravity(true)
        }

        override fun ride(entity: ModelPlatform.ItemDisplayEntity) {
            if (entity !is MinestomItemDisplayEntity) error("Cannot ride other platforms.")
            entity.entity.addPassenger(this.entity)
        }

        override fun teleport(to: Vector3dc) {
            entity.teleport(Pos(to.x(), to.y(), to.z()))
        }

        override fun translate(to: Vector3dc) {
            val location = Pos(to.x(), to.y(), to.z())
            entity.editEntityMeta(ItemDisplayMeta::class.java) {
                it.translation = location
            }
            debugName.teleport(entity.position.add(location))
        }

        override fun scale(by: Vector3dc) {
            entity.editEntityMeta(ItemDisplayMeta::class.java) {
                it.scale = Vec(by.x(), by.y(), by.z())
            }
        }

        override fun rotateRightHanded(rightRotation: Quaterniondc) {
            entity.editEntityMeta(ItemDisplayMeta::class.java) {
                it.rightRotation = floatArrayOf(rightRotation.x().toFloat(), rightRotation.y().toFloat(), rightRotation.z().toFloat(), rightRotation.w().toFloat())
            }
        }
    }
}