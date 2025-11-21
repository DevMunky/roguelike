package dev.munky.modelrenderer.skeleton

import dev.munky.roguelike.common.SerialVector3d
import dev.munky.roguelike.common.serialization.UUIDSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

typealias UUID = @Serializable(with = UUIDSerializer::class) java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val clas = Model::class.java
    val file = clas.getResource("/byleth_warrior.bbmodel")!!.readText()
    val model = BBModelJson.decodeFromString<Model>(file)
    println(model.elements)
    println(model.animations)
    println(model.bones)
}

@Serializable
data class Model(
    val meta: Meta,
    val name: String,
    val modelIdentifier: String,
    val visibleBox: SerialVector3d,
    // val variablePlaceholders: ?,
    // val variablePlaceholderButtons: ?,
    // val timelineSetups: List<?>,
    // val unhandledRootFields: List<?>,
    val resolution: Resolution?,
    @SerialName("elements")
    private val rawElements: ArrayList<Cube>,
    val textures: List<Texture>,
    @SerialName("outliner")
    private val rawBones: ArrayList<Bone>,
    @SerialName("animations")
    private val rawAnimations: ArrayList<Animation>,
    val exportOptions: ExportOptions?,
) {
    @Transient
    val elements: Map<UUID, Cube> = HashMap<UUID, Cube>().apply {
        for (e in rawElements) {
            this[e.uuid] = e
        }
        rawElements.clear()
    }

    @Transient
    val bones: Map<UUID, Bone> = HashMap<UUID, Bone>().apply {
        for (bone in rawBones) {
            bone.init(elements)
            this[bone.uuid] = bone
        }
        rawBones.clear()
    }

    @Transient
    val animations: Map<UUID, Animation> = HashMap<UUID, Animation>().apply {
        for (anim in rawAnimations) {
            this[anim.uuid] = anim
        }
        rawAnimations.clear()
    }

    @Serializable
    data class Resolution(
        val width: Int,
        val height: Int,
    )

    @Serializable
    data class ExportOptions(
        val gltf: Gltf
    ) {
        @Serializable
        data class Gltf(
            val animations: Boolean,
            val armature: Boolean,
            val embedTextures: Boolean,
            val encoding: String,
            val scale: Int
        )
    }

    @Serializable
    data class Meta(
        val formatVersion: Double,
        val modelFormat: String,
        val boxUv: Boolean,
    )

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun decodeFromInputStream(stream: InputStream) : Model {
            return BBModelJson.decodeFromStream(serializer(), stream)
        }
    }
}

interface ModelPart {
    val name: String
    val uuid: UUID
}

@Serializable(with = RawModelPart.Serializer::class)
sealed interface RawModelPart {
    object Serializer : KSerializer<RawModelPart> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GroupMember")

        override fun serialize(encoder: Encoder, value: RawModelPart) {
            val encoder = encoder as? JsonEncoder ?: error("GroupMember serialization only supports Json.")
            when (value) {
                is Bone -> encoder.encodeSerializableValue(Bone.serializer(), value)
                is Cube.Ref -> encoder.encodeString(value.uuid.toString())
            }
        }

        override fun deserialize(decoder: Decoder): RawModelPart {
            val decoder = decoder as? JsonDecoder ?: error("GroupMember deserialization only supports Json.")
            val element = decoder.decodeJsonElement()

            return when (element) {
                is JsonPrimitive -> Cube.Ref(UUID.fromString(element.content))
                is JsonObject -> decoder.json.decodeFromJsonElement(Bone.serializer(), element)
                else -> error("Unexpected JSON type: $element")
            }
        }
    }
}