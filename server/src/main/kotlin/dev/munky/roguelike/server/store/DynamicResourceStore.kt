package dev.munky.roguelike.server.store

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.logger
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import net.kyori.adventure.key.Key
import java.nio.file.Path
import kotlin.collections.iterator
import kotlin.io.path.div
import kotlin.io.path.outputStream

open class DynamicResourceStore<T : Any>(
    serializer: KSerializer<T>,
    format: SerialFormat,
    directory: Path,
    key: T.(fileName: Path) -> String,
    val path: T.(id: String) -> Path,
) : ResourceStore<T>(serializer, format, directory, key), IDynamicResourceStore<T> {
    override fun set(key: Key, value: T): T? {
        val file = path(value, key.value())
        decodedFiles[key] = file
        return entries.put(key, value)
    }

    override suspend fun save() {
        for ((key, e) in entries) decodeContext.launch {
            try {
                val file = decodedFiles[key] ?: run {
                    LOGGER.warn("Entry '$key' has no associated file, cannot save.")
                    return@launch
                }
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

    companion object {
        private val LOGGER = logger {}
        // TODO autosave task
    }
}