package dev.munky.roguelike.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID
import kotlin.reflect.KClass

object UUIDSerializer : KSerializer<UUID> by String.serializer().xmap(
    UUID::toString,
    UUID::fromString
)

fun UUID.serializer() : KSerializer<UUID> = UUIDSerializer

object KClassSerializer : KSerializer<KClass<*>> by String.serializer().xmap(
    { qualifiedName!! }, { Class.forName(this).kotlin }
)

fun KClass<*>.serializer() : KSerializer<KClass<*>> = KClassSerializer