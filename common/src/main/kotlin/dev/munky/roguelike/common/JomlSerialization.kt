package dev.munky.roguelike.common

import dev.munky.roguelike.common.serialization.xmap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.joml.Vector2d
import org.joml.Vector2dc
import org.joml.Vector2i
import org.joml.Vector2ic
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.joml.Vector3ic
import org.joml.Vector4d
import org.joml.Vector4dc

typealias SerialVector2d = @Serializable(with = Vector2dSerializer::class) Vector2dc
object Vector2dSerializer : KSerializer<Vector2dc> by ListSerializer(Double.serializer()).xmap(
    { listOf(x(), y()) },
    { Vector2d(this[0], this[1]) }
)

typealias SerialVector2i = @Serializable(with = Vector2iSerializer::class) Vector2ic
object Vector2iSerializer : KSerializer<Vector2ic> by ListSerializer(Int.serializer()).xmap(
    { listOf(x(), y()) },
    { Vector2i(this[0], this[1]) }
)

typealias SerialVector3d = @Serializable(with = Vector3dSerializer::class) Vector3dc
object Vector3dSerializer : KSerializer<Vector3dc> by ListSerializer(Double.serializer()).xmap(
    { listOf(x(), y(), z()) },
    { Vector3d(this[0], this[1], this[2]) }
)

typealias SerialVector3i = @Serializable(with = Vector3iSerializer::class) Vector3ic
object Vector3iSerializer : KSerializer<Vector3ic> by ListSerializer(Int.serializer()).xmap(
    { listOf(x(), y(), z()) },
    { Vector3i(this[0], this[1], this[2]) }
)

typealias SerialVector4d = @Serializable(with = Vector4dSerializer::class) Vector4dc
object Vector4dSerializer : KSerializer<Vector4dc> by ListSerializer(Double.serializer()).xmap(
    { listOf(x(), y(), z(), w()) },
    { Vector4d(this[0], this[1], this[2], this[3]) }
)