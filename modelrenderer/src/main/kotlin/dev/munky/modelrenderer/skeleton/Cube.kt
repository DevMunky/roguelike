package dev.munky.modelrenderer.skeleton

import dev.munky.modelrenderer.util.Vec3d
import dev.munky.modelrenderer.util.Vec4d
import dev.munky.roguelike.common.serialization.xmap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import org.joml.Matrix4d
import org.joml.Matrix4dc
import java.lang.Math.toRadians

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

    val from: Vec3d,
    val to: Vec3d,
    val rotation: Vec3d? = Vec3d(.0, .0, .0),
    @SerialName("origin") val pivot: Vec3d,
    val faces: Faces,
    val type: Type,
    override val uuid: UUID,
) : ModelPart {
    @Transient
    override val transform: Matrix4dc = Matrix4d().apply {
        val midX = (from.x + to.x) / 2.0
        val midY = (from.y + to.y) / 2.0
        val midZ = (from.z + to.z) / 2.0
        translation(midX * 0.4, midY * 0.4, midZ * 0.4)
        // Maybe rotateLocal?
        rotation?.let {
            rotateXYZ(toRadians(it.x), toRadians(it.y), toRadians(it.z))
        }
    }

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
    data class Face(val texture: Int?, val uv: Vec4d)

    @Serializable
    data class Ref(val uuid: UUID) : RawModelPart
}