package dev.munky.modelrenderer.skeleton

import dev.munky.modelrenderer.util.Vec3d
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4d
import org.joml.Matrix4dc

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
    override val transform: Matrix4dc = Matrix4d().apply {
        translate(origin.x * 0.4, origin.y * 0.4, origin.z * 0.4)
        rotateXYZ(rotation.x, rotation.y, rotation.z)
    }

    internal fun init(elements: Map<UUID, Cube>) {
        val children = HashMap<UUID, ModelPart>()
        for (child in rawChildren) {
            when (child) {
                is Bone -> {
                    children[child.uuid] = child
                    child.init(elements)
                }
                is Cube.Ref -> children[child.uuid] = elements[child.uuid] ?: error("Unknown element reference '${child.uuid}'.")
            }
        }
        this.children = children
        rawChildren.clear()
    }
}