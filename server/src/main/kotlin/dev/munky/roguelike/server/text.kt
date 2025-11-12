package dev.munky.roguelike.server

import kotlinx.serialization.json.JsonNamingStrategy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.PatternReplacementResult
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.serializer.ComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.MinecraftServer
import org.slf4j.Logger
import java.util.regex.MatchResult
import kotlin.text.iterator

fun text(content: String): Component = Component.text(content)

val DEFAULT_MINIMESSAGE = MiniMessage.builder()
    .editTags { tagBuilder ->
        tagBuilder.tag(
            "test",
            Tag.preProcessParsed("<red><strikethrough>")
        )
    }
    .build()

val DEFAULT_PLAIN_TEXT = PlainTextComponentSerializer.builder().build()

operator fun Component.plus(other: Component): Component = append(other)
operator fun Component.minus(other: Component): Component = replaceText { it
    .match(other.asString())
    .once()
}

fun literalComponent(block: TextComponent.Builder.() -> Unit) = Component.text(block)
fun String.asLiteralComponent() = Component.text(this)
fun Char.asLiteralComponent() = Component.text(this)
fun Int.asLiteralComponent() = Component.text(this)
fun Long.asLiteralComponent() = Component.text(this)
fun Float.asLiteralComponent() = Component.text(this)
fun Double.asLiteralComponent() = Component.text(this)
fun Boolean.asLiteralComponent() = Component.text(this)

fun String.asComponent(serializer: ComponentSerializer<*, *, String> = DEFAULT_MINIMESSAGE): Component =
    serializer.deserialize(this)

fun Component.asString(deserializer: ComponentSerializer<in Component, *, String> = DEFAULT_PLAIN_TEXT): String =
    deserializer.serialize(this)