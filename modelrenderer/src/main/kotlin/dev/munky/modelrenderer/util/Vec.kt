package dev.munky.modelrenderer.util

import dev.munky.roguelike.common.serialization.xmap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable(with = Vec2d.Serializer::class)
data class Vec2d(val x: Double, val y: Double) {
    object Serializer : KSerializer<Vec2d> by ListSerializer(Double.serializer()).xmap(
        { listOf(x, y) },
        { Vec2d(this[0], this[1]) }
    )
}

@Serializable(with = Vec2i.Serializer::class)
data class Vec2i(val x: Int, val y: Int) {
    object Serializer : KSerializer<Vec2i> by ListSerializer(Int.serializer()).xmap(
        { listOf(x, y) },
        { Vec2i(this[0], this[1]) }
    )
}

@Serializable(with = Vec3d.Serializer::class)
data class Vec3d(val x: Double, val y: Double, val z: Double) {
    object Serializer : KSerializer<Vec3d> by ListSerializer(Double.serializer()).xmap(
        { listOf(x, y, z) },
        { Vec3d(this[0], this[1], this[2]) }
    )
}

@Serializable(with = Vec4d.Serializer::class)
data class Vec4d(val x: Double, val y: Double, val z: Double, val w: Double) {
    object Serializer : KSerializer<Vec4d> by ListSerializer(Double.serializer()).xmap(
        { listOf(x, y, z, w) },
        { Vec4d(this[0], this[1], this[2], this[3]) }
    )
}