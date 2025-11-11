package dev.munky.roguelike.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

fun <T, R> KSerializer<T>.xmap(encode: R.() -> T, decode: T.() -> R) : KSerializer<R> = object: KSerializer<R> {
    override val descriptor: SerialDescriptor = this@xmap.descriptor
    override fun serialize(encoder: Encoder, value: R) = encoder.encodeSerializableValue(this@xmap, value.encode())
    override fun deserialize(decoder: Decoder): R = decoder.decodeSerializableValue(this@xmap).decode()
}