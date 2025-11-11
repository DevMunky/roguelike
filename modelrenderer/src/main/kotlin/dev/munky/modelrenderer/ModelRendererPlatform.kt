package dev.munky.modelrenderer

import dev.munky.modelrenderer.entity.AbstractModelEntity
import org.joml.Matrix4d

interface ModelRendererPlatform {
    fun spawnItemDisplay(of: AbstractModelEntity, model: String) : MCItemDisplay
    fun spawnInteraction()

    interface MCItemDisplay {
        fun apply(matrix: Matrix4d)
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