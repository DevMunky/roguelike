package dev.munky.modelrenderer.skeleton

import kotlinx.serialization.Serializable

@Serializable
data class Texture(
    val path: String,
    val name: String,
    val folder: String,
    val namespace: String,
    val id: String,
    val group: String?,
    val width: Double,
    val height: Double,
    val uvWidth: Double,
    val uvHeight: Double,
    val particle: Boolean,
    val useAsDefault: Boolean,
    val layersEnabled: Boolean,
    // val syncToProject: String
    val renderMode: String,
    val renderSides: String,
    val frameTime: Double,
    val frameOrder: String,
    val frameOrderType: Animation.LoopMode,
    val frameInterpolate: Boolean,
    val visible: Boolean,
    val internal: Boolean,
    val saved: Boolean,
    /**
     * Base64 string of the texture.
     */
    val source: String,
    val uuid: UUID,
)