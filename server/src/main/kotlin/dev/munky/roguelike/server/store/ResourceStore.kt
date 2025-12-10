package dev.munky.roguelike.server.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import java.nio.file.Path

open class ResourceStore<T : Any>(
    serializer: KSerializer<T>,
    format: SerialFormat,
    override val directory: Path,
    key: T.(fileName: Path) -> String
) : MappedResourceStore<T, T>(serializer, format, directory, { key(this, it) to this })