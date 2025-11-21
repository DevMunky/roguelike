package dev.munky.roguelike.server.store

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.common.snakeCase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.ListSerializer
import net.kyori.adventure.key.Key
import net.kyori.adventure.key.Keyed
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.walk

sealed interface Store<T : Any> {
    operator fun get(key: Key): T?
    operator fun get(value: String): T? = get(Key.key(namespace(), value))

    fun getOrThrow(key: Key): T = get(key) ?: error("No entry with key '$key' exists in '${id()}'.")

    fun namespace() = this::class.simpleName!!.snakeCase()
    fun id() = Key.key("store:${namespace()}")
}

sealed interface ResourceStore<T : Any> : Store<T> {
    val directory: Path
    suspend fun load()

    override fun namespace(): String = directory.nameWithoutExtension.snakeCase()
    override fun id() = Key.key("resource_store:${namespace()}")
}

sealed interface DynamicStore<T : Any> : Store<T> {
    operator fun set(key: Key, value: T) : T?

    override fun id() = Key.key("dynamic_store:${namespace()}")
}

sealed interface DynamicResourceStore<T : Any> : ResourceStore<T>, DynamicStore<T> {
    suspend fun save()

    override fun id() = Key.key("dynamic_resource_store:${namespace()}")
}

open class ResourceStoreImpl<T : Any>(
    protected val serializer: KSerializer<T>,
    protected val format: SerialFormat,
    override val directory: Path
) : ResourceStore<T> {
    protected val entries = ConcurrentHashMap<Key, T>()
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(5)) + CoroutineExceptionHandler { context, throwable ->
        LOGGER.error("Exception caught discovering entry in '${id()}'", throwable)
    }

    override fun get(key: Key): T? = entries[key]

    override suspend fun load() {
        val files = directory.walk().toList().filter { !it.isDirectory() }
        entries.clear()
        if (files.isEmpty()) return
        for (file in files) scope.launch {
            try {
                val bytes = file.inputStream().use {
                    it.readBytes()
                }
                val e =when (format) {
                    is StringFormat -> format.decodeFromString(serializer, bytes.decodeToString())
                    is BinaryFormat -> format.decodeFromByteArray(serializer, bytes)
                    else -> error("Unsupported format: $format")
                }
                val key = Key.key(namespace(), file.nameWithoutExtension)
                entries[key] = e
            } catch (t: Throwable) {
                LOGGER.error("Exception caught loading file '${file}' in '${id()}'.", t)
            }
        }
        LOGGER.info("Finished load for '${id()}'")
    }

    companion object {
        val LOGGER = logger {}
    }
}

open class DynamicResourceStoreImpl<T : Any>(
    serializer: KSerializer<T>,
    format: SerialFormat,
    directory: Path
) : ResourceStoreImpl<T>(serializer, format, directory), DynamicResourceStore<T> {
    override fun set(key: Key, value: T): T? = entries.put(key, value)

    override suspend fun save() {
        for ((key, e) in entries) scope.launch {
            try {
                val file = directory / key.value()
                val data = when (format) {
                    is StringFormat -> format.encodeToString(serializer, e).encodeToByteArray()
                    is BinaryFormat -> format.encodeToByteArray(serializer, e)
                    else -> error("Unsupported format: $format")
                }
                file.outputStream().use {
                    it.write(data)
                }
            } catch (t: Throwable) {
                LOGGER.error("Exception caught saving file '${key}' in '${id()}'.", t)
            }
        }
    }

    override fun id() = Key.key("dynamic_resource_store:${namespace()}")
}