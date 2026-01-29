package dev.munky.roguelike.server.store

import dev.munky.roguelike.common.snakeCase
import net.kyori.adventure.key.Key
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

sealed interface IStore<T : Any> : Iterable<T> {
    operator fun get(key: Key): T?
    operator fun get(value: String): T? = get(keyFrom(value))

    fun getOrThrow(key: Key): T = get(key) ?: error("No entry with key '$key' exists in '${id()}'.")
    fun getOrThrow(value: String): T = getOrThrow(keyFrom(value))

    fun namespace() = this::class.simpleName!!.snakeCase()
    fun id() = Key.key("store:${namespace()}")

    fun keyFrom(value: String) : Key = Key.key(namespace(), value)
}

sealed interface IResourceStore<T : Any> : IStore<T> {
    val directory: Path
    suspend fun load()

    override fun namespace(): String = directory.nameWithoutExtension.snakeCase()
    override fun id() = Key.key("resource_store:${namespace()}")
}

sealed interface IDynamicStore<T : Any> : IStore<T> {
    operator fun set(key: Key, value: T) : T?

    override fun id() = Key.key("dynamic_store:${namespace()}")
}

sealed interface IDynamicResourceStore<T : Any> : IResourceStore<T>, IDynamicStore<T> {
    suspend fun save()

    override fun id() = Key.key("dynamic_resource_store:${namespace()}")
}