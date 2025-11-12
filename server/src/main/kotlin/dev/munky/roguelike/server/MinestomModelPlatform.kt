package dev.munky.roguelike.server

import dev.munky.modelrenderer.ModelPlatform
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
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

        override fun spawnInteraction() {
            TODO("Not yet implemented")
        }

        override fun spawnItemDisplay(
            x: Double,
            y: Double,
            z: Double,
            model: String
        ): ModelPlatform.ItemDisplayEntity {
            val entity = Entity(EntityType.ITEM_DISPLAY)
            entity.setInstance(instance, Pos(x, y, z))
            return MinestomItemDisplayEntity(entity)
        }
    }

    class MinestomItemDisplayEntity(val entity: Entity) : ModelPlatform.ItemDisplayEntity {
        override fun move(to: Vector3dc) {
            entity.teleport(Pos(to.x(), to.y(), to.z()))
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