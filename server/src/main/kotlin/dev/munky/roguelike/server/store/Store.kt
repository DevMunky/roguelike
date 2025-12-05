package dev.munky.roguelike.server.store

import dev.munky.roguelike.common.logger
import dev.munky.roguelike.common.snakeCase
import kotlinx.coroutines.*
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import net.kyori.adventure.key.Key
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.fileStore
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.walk

sealed interface Store<T : Any> : Iterable<T> {
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

open class TransformingResourceStoreImpl<S: Any, T: Any>(
    val serializer: KSerializer<S>,
    val format: SerialFormat,
    override val directory: Path,
    private val transform: suspend S.(fileName: String) -> Pair<String, T>
) : ResourceStore<T> {
    protected val entries = ConcurrentHashMap<Key, T>()

    protected val scope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.IO.limitedParallelism(5)) +
            CoroutineExceptionHandler { context, throwable ->
                context.handleException(throwable)
            }

    open fun CoroutineContext.handleException(throwable: Throwable) {
        LOGGER.error("Exception caught ${get(CoroutineName)} in '${id()}'", throwable)
    }

    override suspend fun load() {
        val files = directory.toFile().listFiles { !it.isDirectory() }?.toList() ?: emptyList()
        if (files.isEmpty()) return
        coroutineScope {
            for (file in files) (scope + currentCoroutineContext() + CoroutineName("decoding file '${file.path}'")).launch {
                val bytes = file.inputStream().use {
                    it.readBytes()
                }
                val e = decode(bytes)
                handleDecodedValue(e, file.nameWithoutExtension)
            }
        }
        LOGGER.info("Finished load for '${id()}'")
    }

    override fun get(key: Key): T? = entries[key]

    open fun decode(bytes: ByteArray) : S = when (val f = format) {
        is StringFormat -> f.decodeFromString(serializer, bytes.decodeToString())
        is BinaryFormat -> f.decodeFromByteArray(serializer, bytes)
        else -> error("Unsupported format: $format")
    }

    open suspend fun handleDecodedValue(data: S, fileName: String) {
        val entry = transform(data, fileName).let { (k, v) -> Key.key(namespace(), k) to v }
        entries += entry
        LOGGER.info("Loaded '${entry.first}' from '${fileName}'.")
    }

    override fun iterator(): Iterator<T> = entries.values.iterator()

    companion object {
        val LOGGER = logger {}
    }
}

open class ResourceStoreImpl<T : Any>(
    serializer: KSerializer<T>,
    format: SerialFormat,
    override val directory: Path,
    key: T.(fileName: String) -> String
) : TransformingResourceStoreImpl<T, T>(serializer, format, directory, { key(this, it) to this })

open class DynamicResourceStoreImpl<T : Any>(
    serializer: KSerializer<T>,
    format: SerialFormat,
    directory: Path,
    key: T.(fileName: String) -> String
) : ResourceStoreImpl<T>(serializer, format, directory, key), DynamicResourceStore<T> {
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