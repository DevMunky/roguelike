package dev.munky.roguelike.common.serialization

import kotlinx.serialization.KSerializer
import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtByteArray
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtDouble
import net.benwoodworth.knbt.NbtFloat
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtIntArray
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLong
import net.benwoodworth.knbt.NbtLongArray
import net.benwoodworth.knbt.NbtShort
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.internal.NbtTagType
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.BinaryTagType
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.ByteArrayBinaryTag
import net.kyori.adventure.nbt.ByteBinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.DoubleBinaryTag
import net.kyori.adventure.nbt.FloatBinaryTag
import net.kyori.adventure.nbt.IntArrayBinaryTag
import net.kyori.adventure.nbt.IntBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import net.kyori.adventure.nbt.LongArrayBinaryTag
import net.kyori.adventure.nbt.LongBinaryTag
import net.kyori.adventure.nbt.ShortBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag

object BinaryTagSerializer : KSerializer<BinaryTag> by NbtTag.serializer().xmap(
    { toKNbt() },
    { toAdventure() }
)

fun NbtTag.toAdventure() : BinaryTag = when (this) {
    is NbtByte -> IntBinaryTag.intBinaryTag(value.toInt())
    is NbtShort -> ShortBinaryTag.shortBinaryTag(value)
    is NbtInt -> IntBinaryTag.intBinaryTag(value)
    is NbtLong -> LongBinaryTag.longBinaryTag(value)
    is NbtFloat -> FloatBinaryTag.floatBinaryTag(value)
    is NbtDouble -> DoubleBinaryTag.doubleBinaryTag(value)

    is NbtByteArray -> ByteArrayBinaryTag.byteArrayBinaryTag(*toByteArray())
    is NbtIntArray -> IntArrayBinaryTag.intArrayBinaryTag(*toIntArray())
    is NbtLongArray -> LongArrayBinaryTag.longArrayBinaryTag(*toLongArray())

    is NbtString -> StringBinaryTag.stringBinaryTag(value)

    is NbtList<*> -> (this as List<NbtTag>).toAdventure()
    is NbtCompound -> CompoundBinaryTag.from(mapValues { it.value.toAdventure() })
}

fun List<NbtTag>.toAdventure() : ListBinaryTag {
    val e = map { it.toAdventure() }
    val type = e.firstOrNull()?.type() ?: return ListBinaryTag.empty()
    return ListBinaryTag.listBinaryTag(type, e)
}

val NbtTag.adventureType: BinaryTagType<*> get() = when (this) {
    is NbtByte -> BinaryTagTypes.BYTE
    is NbtShort -> BinaryTagTypes.SHORT
    is NbtInt -> BinaryTagTypes.INT
    is NbtLong -> BinaryTagTypes.LONG
    is NbtFloat -> BinaryTagTypes.FLOAT
    is NbtDouble -> BinaryTagTypes.DOUBLE

    is NbtByteArray -> BinaryTagTypes.BYTE_ARRAY
    is NbtIntArray -> BinaryTagTypes.INT_ARRAY
    is NbtLongArray -> BinaryTagTypes.LONG_ARRAY

    is NbtString -> BinaryTagTypes.STRING

    is NbtList<*> -> BinaryTagTypes.LIST
    is NbtCompound -> BinaryTagTypes.COMPOUND
}

fun BinaryTag.toKNbt() : NbtTag = when (this) {
    is ByteBinaryTag -> NbtByte(value())
    is ShortBinaryTag -> NbtShort(value())
    is IntBinaryTag -> NbtInt(value())
    is LongBinaryTag -> NbtLong(value())
    is FloatBinaryTag -> NbtFloat(value())
    is DoubleBinaryTag -> NbtDouble(value())

    is ByteArrayBinaryTag -> NbtByteArray(value())
    is IntArrayBinaryTag -> NbtIntArray(value())
    is LongArrayBinaryTag -> NbtLongArray(value())

    is StringBinaryTag -> NbtString(value())

    is ListBinaryTag -> when (val type = elementType()) {
        BinaryTagTypes.BYTE -> NbtList(map { it.toKNbt() as NbtByte })
        BinaryTagTypes.SHORT -> NbtList(map { it.toKNbt() as NbtShort })
        BinaryTagTypes.INT -> NbtList(map { it.toKNbt() as NbtInt })
        BinaryTagTypes.LONG -> NbtList(map { it.toKNbt() as NbtLong })
        BinaryTagTypes.FLOAT -> NbtList(map { it.toKNbt() as NbtFloat })
        BinaryTagTypes.DOUBLE -> NbtList(map { it.toKNbt() as NbtDouble })

        BinaryTagTypes.BYTE_ARRAY -> NbtList(map { it.toKNbt() as NbtByteArray })
        BinaryTagTypes.INT_ARRAY -> NbtList(map { it.toKNbt() as NbtIntArray })
        BinaryTagTypes.LONG_ARRAY -> NbtList(map { it.toKNbt() as NbtLongArray })

        BinaryTagTypes.STRING -> NbtList(map { it.toKNbt() as NbtString })

        BinaryTagTypes.LIST -> NbtList(map { it.toKNbt() as NbtList<*> })
        BinaryTagTypes.COMPOUND -> NbtList(map { it.toKNbt() as NbtCompound })
        else -> error("Unsupported list tag type: $this")
    }
    is CompoundBinaryTag -> NbtCompound(associate { it.key to it.value.toKNbt() })
    else -> error("Unsupported tag type: $this")
}