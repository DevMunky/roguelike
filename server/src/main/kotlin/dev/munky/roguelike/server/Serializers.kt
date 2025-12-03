package dev.munky.roguelike.server

import dev.munky.roguelike.common.serialization.xmap
import dev.munky.roguelike.server.MiniMessageSerializer.MINIMESSAGE
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

object MiniMessageSerializer : KSerializer<Component> by String.serializer().xmap(
    { MINIMESSAGE.serialize(this) }, { MINIMESSAGE.deserialize(this) }
) {
    val MINIMESSAGE = MiniMessage.builder().build()
}