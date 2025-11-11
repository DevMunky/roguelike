package dev.munky.roguelike.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> by String.serializer().xmap(
    UUID::toString,
    UUID::fromString
)

fun UUID.serializer() : KSerializer<UUID> = UUIDSerializer