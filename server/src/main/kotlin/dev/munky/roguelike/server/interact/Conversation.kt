package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.levenshtein
import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.*
import dev.munky.roguelike.server.player.RoguePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import java.util.*
import kotlin.math.PI
import kotlin.math.pow

object ConversationRenderer : Renderer {
    data object OnEndFunction : RenderContext.Key<(RoguePlayer, EndState) -> Unit>
    data object OnStartFunction : RenderContext.Key<(RoguePlayer) -> Unit>
    data object SpeakerOrigin : RenderContext.Key<Pos>
    data object InterruptRange : RenderContext.Key<Double>

    private val ongoingConversations = hashMapOf<UUID, Conversation>()

    override suspend fun RenderContext.render() {
        val player = require(RoguePlayer.Companion)
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

        val origin = get(SpeakerOrigin)
        val range = get(InterruptRange)
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
            val zoomThreshold = PI.toFloat() / 6f
            val zoomAmount = 3f
            val interruptThreshold = 3f
            // They just clicked, so they are probably already looking.
            player.fieldViewModifier = zoomAmount
            launch {
                while (isActive) {
                    delay(50)
                    val look = player.position.direction()
                    val straight = origin.sub(player.position)
                    val dist = look.angle(straight).toFloat()
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
        }

        startConvo(player)
    }

    private fun speakerSays(player: RoguePlayer, conversation: Conversation, message: Component) {
        player.sendMessage(Component.newline() + conversation.speakerName + ":".asComponent())
        player.sendMessage(message)
    }

    private suspend fun RenderContext.startConvo(player: RoguePlayer) {
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

    private suspend fun RenderContext.handleInput(player: RoguePlayer, response: String) {
        val conversation = require(Conversation)
        val closestEntry = conversation.stringBranches.entries.associate {
            levenshtein(it.key, response) to it.value
        }.minBy { it.key }

        val closest = if (closestEntry.key > Conversation.Companion.MAXIMUM_RESPONSE_DISTANCE) {
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
    var execute: suspend (RoguePlayer) -> Unit = {}
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
    private var execute: suspend (RoguePlayer) -> Unit = {}
    private var branches: MutableMap<Component, Conversation> = HashMap()
    private var unknownResponse: Component = Component.text("I don't understand.")
    private var interrupt: Component = Component.text("Alright...")

    fun say(message: Component) {
        prompt = message
    }

    fun interrupt(message: Component) {
        interrupt = message
    }

    fun execute(block: suspend (RoguePlayer) -> Unit) {
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