package dev.munky.modelrenderer.skeleton

import dev.munky.modelrenderer.util.Vec3d
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4d

@Serializable
data class Bone(
    override val name: String,
    val origin: Vec3d,
    val rotation: Vec3d = Vec3d(.0, 1.0, .0),
    val color: Int,
    override val uuid: UUID,
    val export: Boolean,
    val mirrorUv: Boolean,
    val isOpen: Boolean = false,
    val locked: Boolean,
    val visibility: Boolean,
    val autoUv: Int = 0,
    @SerialName("children") val rawChildren: ArrayList<RawModelPart>
) : RawModelPart, ModelPart {
    @Transient
    lateinit var children: Map<UUID, ModelPart>
        private set

    @Transient
    override val transform: Matrix4d = Matrix4d().apply {
        translate(origin.x, origin.y, origin.z)
    }

    internal fun init(elements: Map<UUID, Cube>) {
        val children = HashMap<UUID, ModelPart>()
        for (child in rawChildren) {
            when (child) {
                is Bone -> children[child.uuid] = child
                is Cube.Ref -> children[child.uuid] = elements[child.uuid] ?: error("Unknown element reference '${child.uuid}'.")
            }
        }
        this.children = children
        rawChildren.clear()
    }
}