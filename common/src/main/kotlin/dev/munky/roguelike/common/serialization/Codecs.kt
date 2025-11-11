@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")
package dev.munky.roguelike.common.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*

@PublishedApi
internal fun <R> UNSAFE_deserialize(descriptorF: SerialDescriptor, ctor: (Array<Any?>)-> R, vararg serializers: KSerializer<*>): DeserializationStrategy<R> = object: DeserializationStrategy<R> {
    override val descriptor: SerialDescriptor = descriptorF
    override fun deserialize(decoder: Decoder): R = decoder.decodeStructure(descriptor) {
        val fields = Array<Any?>(descriptor.elementsCount) { null }
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            val ser = serializers[index]
            fields[index] = decodeSerializableElement(descriptor, index, ser)
        }

        val missing = mutableListOf<String>()
        for ((i, field )in fields.withIndex()) {
            if (field == null) missing += descriptor.getElementName(i)
        }
        if (missing.isNotEmpty()) throw MissingFieldException(missing, descriptor.serialName)

        ctor(fields)
    }
}

@PublishedApi
internal fun <R> UNSAFE_serialize(descriptorF: SerialDescriptor, fields: R.() -> Array<Any?>, vararg serializers: KSerializer<*>): SerializationStrategy<R> = object: SerializationStrategy<R> {
    override val descriptor: SerialDescriptor = descriptorF
    override fun serialize(encoder: Encoder, value: R) = encoder.encodeStructure(descriptor) {
        val f = fields(value)
        for ((i, s) in serializers.withIndex()) {
            serializeElement(descriptor, i, s as KSerializer<Any?>, f[i])
        }
    }

    /**
     * Doesnt encode a class discriminator, yet still deserializes polymorphic data just fine.
     */
    private fun <T> CompositeEncoder.serializeElement(descriptor: SerialDescriptor, index: Int, serializer: KSerializer<T>, value: T) {
        encodeSerializableElement(descriptor, index, serializer, value)
    }
}

inline fun <T1, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    noinline ctor: (T1) -> R
): KSerializer<R> = object: KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, { ctor(it[0] as T1) }, st1)
    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, { arrayOf(gt1()) }, st1)
    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    noinline ctor: (T1, T2) -> R
): KSerializer<R> = object: KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, { ctor(it[0] as T1, it[1] as T2) }, st1, st2)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, { arrayOf(gt1(), gt2()) }, st1, st2)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    noinline ctor: (T1, T2, T3) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3)
    }, st1, st2, st3)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, { arrayOf(gt1(), gt2(), gt3()) }, st1, st2, st3)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    noinline ctor: (T1, T2, T3, T4) -> R
): KSerializer<R> = object: KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4)
    }, st1, st2, st3, st4)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, { arrayOf(gt1(), gt2(), gt3(), gt4()) }, st1, st2, st3, st4)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, T5, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    nt5: String, crossinline gt5: R.() -> T5, st5: KSerializer<T5>,
    noinline ctor: (T1, T2, T3, T4, T5) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
        element(nt5, st5.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4, it[4] as T5)
    }, st1, st2, st3, st4, st5)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, {
        arrayOf(gt1(), gt2(), gt3(), gt4(), gt5())
    }, st1, st2, st3, st4, st5)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, T5, T6, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    nt5: String, crossinline gt5: R.() -> T5, st5: KSerializer<T5>,
    nt6: String, crossinline gt6: R.() -> T6, st6: KSerializer<T6>,
    noinline ctor: (T1, T2, T3, T4, T5, T6) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
        element(nt5, st5.descriptor)
        element(nt6, st6.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4, it[4] as T5, it[5] as T6)
    }, st1, st2, st3, st4, st5, st6)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, {
        arrayOf(gt1(), gt2(), gt3(), gt4(), gt5(), gt6())
    }, st1, st2, st3, st4, st5, st6)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, T5, T6, T7, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    nt5: String, crossinline gt5: R.() -> T5, st5: KSerializer<T5>,
    nt6: String, crossinline gt6: R.() -> T6, st6: KSerializer<T6>,
    nt7: String, crossinline gt7: R.() -> T7, st7: KSerializer<T7>,
    noinline ctor: (T1, T2, T3, T4, T5, T6, T7) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
        element(nt5, st5.descriptor)
        element(nt6, st6.descriptor)
        element(nt7, st7.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4, it[4] as T5, it[5] as T6, it[6] as T7)
    }, st1, st2, st3, st4, st5, st6, st7)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, {
        arrayOf(gt1(), gt2(), gt3(), gt4(), gt5(), gt6(), gt7())
    }, st1, st2, st3, st4, st5, st6, st7)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, T5, T6, T7, T8, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    nt5: String, crossinline gt5: R.() -> T5, st5: KSerializer<T5>,
    nt6: String, crossinline gt6: R.() -> T6, st6: KSerializer<T6>,
    nt7: String, crossinline gt7: R.() -> T7, st7: KSerializer<T7>,
    nt8: String, crossinline gt8: R.() -> T8, st8: KSerializer<T8>,
    noinline ctor: (T1, T2, T3, T4, T5, T6, T7, T8) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
        element(nt5, st5.descriptor)
        element(nt6, st6.descriptor)
        element(nt7, st7.descriptor)
        element(nt8, st8.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4, it[4] as T5, it[5] as T6, it[6] as T7, it[7] as T8)
    }, st1, st2, st3, st4, st5, st6, st7, st8)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, {
        arrayOf(gt1(), gt2(), gt3(), gt4(), gt5(), gt6(), gt7(), gt8())
    }, st1, st2, st3, st4, st5, st6, st7, st8)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    nt5: String, crossinline gt5: R.() -> T5, st5: KSerializer<T5>,
    nt6: String, crossinline gt6: R.() -> T6, st6: KSerializer<T6>,
    nt7: String, crossinline gt7: R.() -> T7, st7: KSerializer<T7>,
    nt8: String, crossinline gt8: R.() -> T8, st8: KSerializer<T8>,
    nt9: String, crossinline gt9: R.() -> T9, st9: KSerializer<T9>,
    noinline ctor: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
        element(nt5, st5.descriptor)
        element(nt6, st6.descriptor)
        element(nt7, st7.descriptor)
        element(nt8, st8.descriptor)
        element(nt9, st9.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4, it[4] as T5, it[5] as T6, it[6] as T7, it[7] as T8, it[8] as T9)
    }, st1, st2, st3, st4, st5, st6, st7, st8, st9)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, {
        arrayOf(gt1(), gt2(), gt3(), gt4(), gt5(), gt6(), gt7(), gt8(), gt9())
    }, st1, st2, st3, st4, st5, st6, st7, st8, st9)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}

inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, reified R> codec(
    nt1: String, crossinline gt1: R.() -> T1, st1: KSerializer<T1>,
    nt2: String, crossinline gt2: R.() -> T2, st2: KSerializer<T2>,
    nt3: String, crossinline gt3: R.() -> T3, st3: KSerializer<T3>,
    nt4: String, crossinline gt4: R.() -> T4, st4: KSerializer<T4>,
    nt5: String, crossinline gt5: R.() -> T5, st5: KSerializer<T5>,
    nt6: String, crossinline gt6: R.() -> T6, st6: KSerializer<T6>,
    nt7: String, crossinline gt7: R.() -> T7, st7: KSerializer<T7>,
    nt8: String, crossinline gt8: R.() -> T8, st8: KSerializer<T8>,
    nt9: String, crossinline gt9: R.() -> T9, st9: KSerializer<T9>,
    nt10: String, crossinline gt10: R.() -> T10, st10: KSerializer<T10>,
    noinline ctor: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> R
): KSerializer<R> = object : KSerializer<R> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(R::class.simpleName!!) {
        element(nt1, st1.descriptor)
        element(nt2, st2.descriptor)
        element(nt3, st3.descriptor)
        element(nt4, st4.descriptor)
        element(nt5, st5.descriptor)
        element(nt6, st6.descriptor)
        element(nt7, st7.descriptor)
        element(nt8, st8.descriptor)
        element(nt9, st9.descriptor)
        element(nt10, st10.descriptor)
    }

    val deserialize: DeserializationStrategy<R> = UNSAFE_deserialize(descriptor, {
        ctor(it[0] as T1, it[1] as T2, it[2] as T3, it[3] as T4, it[4] as T5,
            it[5] as T6, it[6] as T7, it[7] as T8, it[8] as T9, it[9] as T10)
    }, st1, st2, st3, st4, st5, st6, st7, st8, st9, st10)

    override fun deserialize(decoder: Decoder): R = deserialize.deserialize(decoder)

    val serialize: SerializationStrategy<R> = UNSAFE_serialize(descriptor, {
        arrayOf(gt1(), gt2(), gt3(), gt4(), gt5(),
            gt6(), gt7(), gt8(), gt9(), gt10())
    }, st1, st2, st3, st4, st5, st6, st7, st8, st9, st10)

    override fun serialize(encoder: Encoder, value: R) = serialize.serialize(encoder, value)
}