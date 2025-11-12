package dev.munky.modelrenderer

import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.UUID

interface ModelPlatform {
    fun levelOf(uuid: UUID) : Level

    interface ItemDisplayEntity {
        fun move(to: Vector3dc)
        fun scale(by: Vector3dc)
        fun rotateRightHanded(rightRotation: Quaterniondc)
    }
    interface Level {
        fun playSound(at: Vector3d, soundId: String)
        fun spawnInteraction()
        fun spawnItemDisplay(x: Double, y: Double, z: Double, model: String) : ItemDisplayEntity
    }

    companion object {
        private var registeredPlatform: ModelPlatform? = null
        fun register(platform: ModelPlatform) : ModelPlatform {
            if (registeredPlatform == null) error("A platform is already registered")
            registeredPlatform = platform
            return platform
        }
        fun platform() : ModelPlatform {
            return registeredPlatform ?: error("There is no registered platform. Register one with ${ModelPlatform::class.qualifiedName}.register(Platform).")
        }
    }
}