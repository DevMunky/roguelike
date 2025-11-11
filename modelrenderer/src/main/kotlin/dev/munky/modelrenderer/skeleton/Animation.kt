package dev.munky.modelrenderer.skeleton

import dev.munky.roguelike.common.serialization.UUIDSerializer
import dev.munky.roguelike.common.serialization.xmap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.joml.Matrix4d

object EmptyStringAsZeroDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EmptyStringAsZeroDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double {
        return try {
            when (val input = decoder.decodeString()) {
                "" -> 0.0
                else -> input.toDouble()
            }
        } catch (_: SerializationException) {
            // In case it's not a string (JSON number, for example)
            (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.doubleOrNull ?: 0.0
        }
    }
}

@Serializable(with = Animation.Serializer::class)
data class Animation(
    val uuid: UUID,
    val name: String,
    val loop: LoopMode,
    val override: Boolean,
    val length: Double,
    val snapping: Int,
    val selected: Boolean,
    // "anim_time_update": "",
    //      "blend_weight": "",
    @Serializable(with = EmptyStringAsZeroDoubleSerializer::class)
    val startDelay: Double = .0,
    @Serializable(with = EmptyStringAsZeroDoubleSerializer::class)
    val loopDelay: Double = .0,
    val animators: Map<UUID, Animator>,
    val effects: Animator?
) {
    object Serializer : KSerializer<Animation> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Animation")

        override fun serialize(encoder: Encoder, value: Animation) { TODO() }

        override fun deserialize(decoder: Decoder): Animation {
            val decoder = decoder as? JsonDecoder ?: error("Animation deserialization only supports JSON.")
            val json = decoder.json
            val root = decoder.decodeJsonElement().jsonObject
            val uuid = json.decodeFromJsonElement(UUIDSerializer, root["uuid"]!!)
            val name = root["name"]!!.jsonPrimitive.content
            val loop = json.decodeFromJsonElement(LoopMode.serializer(), root["loop"]!!)
            val override = root["override"]!!.jsonPrimitive.boolean
            val length = root["length"]!!.jsonPrimitive.double
            val snapping = root["snapping"]!!.jsonPrimitive.int
            val selected = root["selected"]!!.jsonPrimitive.boolean
            val startDelay = json.decodeFromJsonElement(EmptyStringAsZeroDoubleSerializer, root["start_delay"]!!)
            val loopDelay = json.decodeFromJsonElement(EmptyStringAsZeroDoubleSerializer, root["loop_delay"]!!)
            val animatorsJson = root["animators"]!!.jsonObject
            var effects: Animator? = null
            val animators = HashMap<UUID, Animator>()
            for ((uidStr, animatorJson) in animatorsJson.entries) {
                val animator = json.decodeFromJsonElement(Animator.serializer(), animatorJson)
                if (uidStr == "effects") effects = animator
                else animators[UUID.fromString(uidStr)] = animator
            }
            return Animation(uuid, name, loop, override, length, snapping, selected, startDelay, loopDelay, animators, effects)
        }
    }

    @Serializable
    data class Animator(
        val name: String,
        val type: Type,
        val keyframes: List<KeyFrame>
    ) {
        @Serializable(with = KeyFrame.Serializer::class)
        data class KeyFrame(
            val channel: Channel,
            val dataPoints: List<DataPoint>,
            val uuid: UUID,
            val time: Double,
            val color: Int,
            val interpolation: Interpolation,
        ) {
            @Transient
            val transform: Matrix4d? = when (channel) {
                Channel.POSITION -> Matrix4d().apply {
                    for (data in dataPoints) {
                        val data = data as DataPoint.Vec3d
                        translation(data.x, data.y, data.z)
                    }
                }
                else -> null
            }

            enum class Channel {
                POSITION,
                ROTATION,
                SCALE,
                SOUND,
            }

            sealed interface DataPoint {
                data class Vec3d(
                    val x: Double,
                    val y: Double,
                    val z: Double,
                ) : DataPoint
                data class Sound(
                    val effect: String,
                    val file: String,
                ) : DataPoint
            }

            enum class Interpolation {
                CATMULLROM,
                LINEAR
            }

            object Serializer : KSerializer<KeyFrame> by JsonObject.serializer().xmap({ TODO() }, {
                val channel = Channel.valueOf(get("channel")!!.jsonPrimitive.content.uppercase())
                val interpolation = Interpolation.valueOf(get("interpolation")!!.jsonPrimitive.content.uppercase())
                val uuid = UUID.fromString(get("uuid")!!.jsonPrimitive.content)
                val time = get("time")!!.jsonPrimitive.double
                val color = get("color")!!.jsonPrimitive.int
                val data = ArrayList<DataPoint>()
                val dataArray = get("data_points")!!.jsonArray
                when (channel) {
                    Channel.ROTATION, Channel.POSITION, Channel.SCALE -> for (datum in dataArray.map { it.jsonObject }) {
                        val x = datum["x"]!!.jsonPrimitive.double
                        val y = datum["y"]!!.jsonPrimitive.double
                        val z = datum["z"]!!.jsonPrimitive.double
                        data += DataPoint.Vec3d(x, y, z)
                    }
                    Channel.SOUND -> for (datum in dataArray.map { it.jsonObject }) {
                        val effect = datum["effect"]!!.jsonPrimitive.content
                        val file = datum["file"]!!.jsonPrimitive.content
                        data += DataPoint.Sound(effect, file)
                    }
                    else -> error("Unhandled keyframe channel $channel : $dataArray")
                }

                KeyFrame(channel, data, uuid, time, color, interpolation)
            })
        }

        @Serializable
        enum class Type {
            @SerialName("bone") BONE,
            @SerialName("cube") CUBE,
            @SerialName("effect") EFFECT,
        }
    }

    @Serializable
    enum class LoopMode {
        @SerialName("once") ONCE,
        @SerialName("loop") LOOP,
        @SerialName("hold") HOLD
    }
}