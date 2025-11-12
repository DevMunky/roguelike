package dev.munky.modelrenderer

import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.UUID

interface ModelRendererPlatform {
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
        private var registeredPlatform: ModelRendererPlatform? = null
        fun register(platform: ModelRendererPlatform) {
            if (registeredPlatform == null) error("A platform is already registered")
            registeredPlatform = platform
        }
        fun platform() : ModelRendererPlatform {
            return registeredPlatform ?: error("There is no registered platform. Register one with ${ModelRendererPlatform::class.qualifiedName}.register(Platform).")
        }
    }
}