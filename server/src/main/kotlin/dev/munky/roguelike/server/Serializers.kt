package dev.munky.roguelike.server

import dev.munky.roguelike.common.serialization.BinaryTagSerializer
import dev.munky.roguelike.common.serialization.xmap
import dev.munky.roguelike.server.MiniMessageSerializer.MINIMESSAGE
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import net.hollowcube.schem.Structure
import net.hollowcube.schem.reader.SchematicReader
import net.hollowcube.schem.writer.SchematicWriter
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.EntityType
import java.io.ByteArrayOutputStream

object MiniMessageSerializer : KSerializer<Component> by String.serializer().xmap(
    { MINIMESSAGE.serialize(this) }, { MINIMESSAGE.deserialize(this) }
) {
    val MINIMESSAGE = MiniMessage.builder().build()
}

object StructureSerializer : KSerializer<Structure> by BinaryTagSerializer.xmap(
    {
        val bytes = SchematicWriter.structure().write(this)
        bytes.inputStream().use {
            BinaryTagIO.reader().read(it, BinaryTagIO.Compression.GZIP)
        }
    },
    {
        val os = ByteArrayOutputStream()
        os.use {
            BinaryTagIO.writer().write((this as CompoundBinaryTag).getCompound(""), it, BinaryTagIO.Compression.GZIP)
        }
        SchematicReader.structure().read(os.toByteArray()) as Structure
    }
)

object EntityTypeSerializer : KSerializer<EntityType> by String.serializer().xmap(
    { key().asString() },
    { EntityType.fromKey(this) ?: error("Unknown entity type '$this'.") },
)