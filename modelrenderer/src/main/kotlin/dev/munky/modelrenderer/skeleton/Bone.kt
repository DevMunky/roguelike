package dev.munky.modelrenderer.skeleton

import dev.munky.modelrenderer.util.SerialVector3d
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3d
import org.joml.Vector3dc

@Serializable
data class Bone(
    override val name: String,
    val origin: SerialVector3d,
    val rotation: SerialVector3d = Vector3d(.0, .0, .0),
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
    val pivot: Vector3dc = Vector3d(-origin.x(), origin.y(), origin.z())

    @Transient
    lateinit var children: Map<UUID, ModelPart>
        private set

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