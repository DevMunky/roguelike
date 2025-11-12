package dev.munky.modelrenderer.skeleton

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy

@OptIn(ExperimentalSerializationApi::class)
val BBModelJson = Json {
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase // do i dont have to make five billion serial name annotations
    allowStructuredMapKeys = true
}