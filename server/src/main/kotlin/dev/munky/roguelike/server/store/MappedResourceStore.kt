package dev.munky.roguelike.server.store

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import net.kyori.adventure.key.Key
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.plusAssign
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path

open class MappedResourceStore<S: Any, T: Any>(
    val serializer: KSerializer<S>,
    val format: SerialFormat,
    override val directory: Path,
    private val transform: suspend S.(filePath: Path) -> Pair<String, T>
) : IResourceStore<T> {
    protected val entries = ConcurrentHashMap<Key, T>()
    protected val decodedFiles = ConcurrentHashMap<Key, Path>()

    protected val decodeContext = SupervisorJob() +
            Dispatchers.IO.limitedParallelism(5) +
            CoroutineExceptionHandler { context, throwable ->
                context.handleException(throwable)
            }

    open fun CoroutineContext.handleException(throwable: Throwable) {
        LOGGER.error("Exception caught ${get(CoroutineName.Key)?.name} in '${id()}'", throwable)
    }

    override suspend fun load() {
        val files = directory.toFile().walk().filter { !it.isDirectory() }.toList()
        if (files.isEmpty()) {
            LOGGER.warn("No files found for '${id()}'.")
            return
        }
        val tasks = ArrayList<Job>()
        for (file in files) tasks += (CoroutineName("decoding file '${file.path}'") + decodeContext).launch {
            val bytes = file.inputStream().use {
                it.readBytes()
            }
            val e = decode(bytes)
            val key = handleDecodedValue(
                e,
                Path(file.path
                    .replace("\\", "/")
                    .let {
                        val i = it.indexOf(namespace() + "/")
                        it.substring(i + namespace().length +  1)
                    })
            )
            this@MappedResourceStore.decodedFiles[key] = file.toPath()
        }
        tasks.joinAll()
        LOGGER.info("Finished load for '${id()}'")
    }

    override fun get(key: Key): T? = entries[key]

    open fun decode(bytes: ByteArray) : S = when (val f = format) {
        is StringFormat -> f.decodeFromString(serializer, bytes.decodeToString())
        is BinaryFormat -> f.decodeFromByteArray(serializer, bytes)
        else -> error("Unsupported format: $format")
    }

    open suspend fun handleDecodedValue(data: S, path: Path) : Key {
        val entry = transform(data, path).let { (k, v) -> Key.key(namespace(), k) to v }
        entries += entry
        LOGGER.info("Loaded '${entry.first}' from '${path}'.")
        return entry.first
    }

    override fun iterator(): Iterator<T> = entries.values.iterator()

    companion object {
        private val LOGGER = logger {}
    }
}