package dev.munky.modelrenderer.skeleton

import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

private val instance = Json {
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase // do i dont have to make five billion serial name annotations
    allowStructuredMapKeys = true
}

object BBModelJson : StringFormat by instance