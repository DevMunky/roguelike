package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.levenshtein
import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.asString
import dev.munky.roguelike.server.interact.Conversation.Companion.MAXIMUM_RESPONSE_DISTANCE
import dev.munky.roguelike.server.player.RoguelikePlayer
import dev.munky.roguelike.server.plus
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import java.util.*
import kotlin.math.pow

interface TalkingInteractable : Interactable {
    val conversation: Conversation

    fun onDialogueEnd(player: RoguelikePlayer, endState: ConversationRenderer.EndState) {}
    fun onDialogueStart(player: RoguelikePlayer) {}

    fun RenderDispatch.buildDispatch() {}

    override fun onInteract(player: RoguelikePlayer) {
        RenderDispatch.with(ConversationRenderer)
            .with(conversation)
            .with(player)
            .with(ConversationRenderer.OnStartFunction, ::onDialogueStart)
            .with(ConversationRenderer.OnEndFunction, ::onDialogueEnd)
            .apply {
                buildDispatch()
            }
            .dispatch()
    }
}

abstract class TalkingEntityCreature(type: EntityType) : EntityCreature(type), TalkingInteractable {
    val range: Double = 5.0

    var targetPlayer: RoguelikePlayer? = null
    var preTargetYaw = 0f
    var preTargetPitch = 0f

    override fun onDialogueStart(player: RoguelikePlayer) {
        preTargetPitch = position.pitch
        preTargetYaw = position.yaw
        targetPlayer = player
    }

    override fun onDialogueEnd(player: RoguelikePlayer, endState: ConversationRenderer.EndState) {
        targetPlayer = null
        setView(preTargetYaw, preTargetPitch)
    }

    override fun RenderDispatch.buildDispatch() {
        with(ConversationRenderer.Origin, position)
        with(ConversationRenderer.Range, range)
    }

    override fun tick(time: Long) {
        if (targetPlayer != null) {
            lookAt(targetPlayer)
        }
        super.tick(time)
    }
}

object ConversationRenderer : Renderer {
    data object OnEndFunction : RenderContext.Key<(RoguelikePlayer, EndState) -> Unit>
    data object OnStartFunction : RenderContext.Key<(RoguelikePlayer) -> Unit>
    data object Origin : RenderContext.Key<Pos>
    data object Range : RenderContext.Key<Double>

    private val ongoingConversations = hashMapOf<UUID, Conversation>()

    override suspend fun RenderContext.render() {
        val player = require(RoguelikePlayer)
        val eventNode = EventNode.event("${Roguelike.NAMESPACE}:conversation_renderer.${player.username}", EventFilter.PLAYER) { it.player == player }

        onDispose {
            MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
            val e = get(EndState)
            val c = require(Conversation)

            if (e == EndState.BUSY) {
                if (ongoingConversations[player.uuid] != c)
                    speakerSays(player, c, "<red>You are busy with something else.".asComponent())
                return@onDispose
            }

            player.fieldViewModifier = 0f
            get(OnEndFunction)?.invoke(player, e)

            synchronized(ongoingConversations) {
                ongoingConversations.remove(player.uuid)
            }

            when (e) {
                EndState.NOT_ENDED -> error("Conversation ended but no end state was set.")
                EndState.INTERRUPTED -> speakerSays(player, c, c.interrupt)
                EndState.COMPLETE,
                EndState.INVALID -> {}
            }
        }

        synchronized(ongoingConversations) {
            if (ongoingConversations.contains(player.uuid)) {
                set(EndState, EndState.BUSY)
                dispose()
                return
            } else ongoingConversations[player.uuid] = require(Conversation)
        }

        get(OnStartFunction)?.invoke(player)

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        eventNode.addListener(PlayerChatEvent::class.java) { e ->
            e.isCancelled = true

            launch {
                handleInput(player, e.rawMessage)
            }
        }

        val origin = get(Origin)
        val range = get(Range)
        // If we are too far away, regardless of look direction, dispose.
        if (origin != null && range != null) eventNode.addListener(PlayerMoveEvent::class.java) { e ->
            val d2 = e.newPosition.distanceSquared(origin)
            if (d2 > range * range) {
                set(EndState, EndState.INTERRUPTED)
                dispose()
            }
        }
        // If we are looking near the origin, fov up. If we look completely away, dispose.
        if (origin != null) {
            val zoomThreshold = 0.15f
            val zoomAmount = 3f
            val interruptThreshold = 3f
            // They just clicked, so they are probably already looking.
            player.fieldViewModifier = zoomAmount
            eventNode.addListener(PlayerMoveEvent::class.java) { e ->
                val look = e.newPosition.direction()
                val straight = origin.sub(e.newPosition).asVec().normalize()
                val dist = straight.sub(look).lengthSquared().toFloat()
                when {
                    dist < zoomThreshold -> {
                        val r = 1 - (dist / zoomThreshold)
                        val amount = (r.pow(5f) * zoomAmount).coerceIn(1e-1f, zoomAmount)
                        player.fieldViewModifier = amount
                    }
                    dist >= interruptThreshold -> {
                        set(EndState, EndState.INTERRUPTED)
                        dispose()
                    }
                    else -> player.fieldViewModifier = 0f
                }
            }
        }

        startConvo(player)
    }

    private fun speakerSays(player: RoguelikePlayer, conversation: Conversation, message: Component) {
        player.sendMessage(Component.newline() + conversation.speakerName + ":".asComponent())
        player.sendMessage(message)
    }

    private suspend fun RenderContext.startConvo(player: RoguelikePlayer) {
        val conversation = require(Conversation)
        speakerSays(player, conversation, conversation.prompt)

        for ((message, _) in conversation.branches) {
            player.sendMessage("<green>-> ".asComponent() + message)
        }

        conversation.execute(player)

        if (conversation.branches.isEmpty()) {
            set(EndState, EndState.COMPLETE)
            dispose()
        }
    }

    private suspend fun RenderContext.handleInput(player: RoguelikePlayer, response: String) {
        val conversation = require(Conversation)
        val closestEntry = conversation.stringBranches.entries.associate {
            levenshtein(it.key, response) to it.value
        }.minBy { it.key }

        val closest = if (closestEntry.key > MAXIMUM_RESPONSE_DISTANCE) {
            player.sendMessage(conversation.speakerName + ":".asComponent())
            player.sendMessage(conversation.unknownResponse)
            set(EndState, EndState.COMPLETE)
            dispose()
            return
        } else closestEntry.value

        set(Conversation, closest)
        startConvo(player)
    }

    enum class EndState : RenderContext.Element {
        NOT_ENDED,
        INVALID,
        BUSY,
        INTERRUPTED,
        COMPLETE;

        override val key: RenderContext.Key<*> get() = Companion

        companion object : RenderContext.StableKey<EndState> {
            override val default: EndState = NOT_ENDED
        }
    }
}

data class Conversation(
    val speakerName: Component,
    val prompt: Component,
    val branches: Map<Component, Conversation>,
    val unknownResponse: Component,
    val interrupt: Component,
    var execute: suspend (RoguelikePlayer) -> Unit = {}
) : RenderContext.Element {
    override val key: RenderContext.Key<*> = Companion

    val stringBranches: Map<String, Conversation> = branches.mapKeys { it.key.asString() }

    companion object : RenderContext.Key<Conversation> {
        const val MAXIMUM_RESPONSE_DISTANCE = 6
    }
}

@DslMarker
annotation class ConversationDsl

@ConversationDsl
class ConversationBuilder(
    private var speakerName: Component
) {
    private var prompt: Component = Component.text("...")
    private var execute: suspend (RoguelikePlayer) -> Unit = {}
    private var branches: MutableMap<Component, Conversation> = HashMap()
    private var unknownResponse: Component = Component.text("I don't understand.")
    private var interrupt: Component = Component.text("Alright...")

    fun say(message: Component) {
        prompt = message
    }

    fun interrupt(message: Component) {
        interrupt = message
    }

    fun execute(block: suspend (RoguelikePlayer) -> Unit) {
        execute = block
    }

    fun response(newSpeaker: Component, message: Component, block: ConversationBuilder.() -> Unit) {
        val branch = ConversationBuilder(newSpeaker).apply(block).build()
        branches[message] = branch
    }

    fun response(message: Component, block: ConversationBuilder.() -> Unit) {
        val branch = ConversationBuilder(speakerName).apply(block).build()
        branches[message] = branch
    }

    fun unknownResponse(message: Component) {
        unknownResponse = message
    }

    fun build() : Conversation = Conversation(speakerName, prompt, branches, unknownResponse, interrupt, execute)
}

fun conversation(speaker: Component, block: ConversationBuilder.() -> Unit) = ConversationBuilder(speaker).apply(block).build()