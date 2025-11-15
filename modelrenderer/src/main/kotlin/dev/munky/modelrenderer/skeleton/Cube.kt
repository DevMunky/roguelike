package dev.munky.modelrenderer.skeleton

import dev.munky.modelrenderer.util.SerialVector3d
import dev.munky.modelrenderer.util.SerialVector4d
import dev.munky.roguelike.common.serialization.xmap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.joml.Vector3d

object BooleanByIntSerializer : KSerializer<Boolean> by Int.serializer().xmap(
    { if (this) 1 else 0 },
    { this != 0 }
)
typealias BooleanByInt = @Serializable(with = BooleanByIntSerializer::class) Boolean

@Serializable
data class Cube(
    override val name: String,
    val boxUv: Boolean,
    val rescale: Boolean,
    val locked: Boolean,
    val lightEmission: BooleanByInt = false,
    val autoUv: Int = 0,
    val color: Int,

    val from: SerialVector3d,
    val to: SerialVector3d,
    val rotation: SerialVector3d? = Vector3d(.0, .0, .0),
    @SerialName("origin") val pivot: SerialVector3d,
    val faces: Faces,
    val type: Type,
    override val uuid: UUID,
) : ModelPart {
    enum class Type {
        @SerialName("bone") BONE,
        @SerialName("cube") CUBE
    }

    @Serializable
    data class Faces(
        val down: Face,
        val up: Face,
        val north: Face,
        val south: Face,
        val west: Face,
        val east: Face,
    )

    @Serializable
    data class Face(val texture: Int?, val uv: SerialVector4d)

    @Serializable
    data class Ref(val uuid: UUID) : RawModelPart
}