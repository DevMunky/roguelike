package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.launch
import dev.munky.roguelike.common.levenshtein
import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandle
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.asComponent
import dev.munky.roguelike.server.asString
import dev.munky.roguelike.server.interact.Conversation.Companion.MAXIMUM_RESPONSE_DISTANCE
import dev.munky.roguelike.server.player.RoguelikePlayer
import dev.munky.roguelike.server.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import java.util.UUID

interface TalkingInteractable : Interactable {
    val conversation: Conversation

    override fun onInteract(player: RoguelikePlayer) {
        RenderDispatch.with(ConversationRenderer)
            .with(conversation)
            .with(player)
            .dispatch()
    }
}

object ConversationRenderer : Renderer {
    private val ongoingConversations = hashSetOf<UUID>()

    override suspend fun RenderContext.render() {
        val player = require(RenderKey.Player) as? RoguelikePlayer ?: return
        synchronized(ongoingConversations) {
            if (ongoingConversations.contains(player.uuid)) {
                return
            } else ongoingConversations.add(player.uuid)
        }
        val eventNode = EventNode.event("roguelike:conversation", EventFilter.PLAYER) { it.player == player }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        eventNode.addListener(PlayerChatEvent::class.java) { e ->
            val c = require(Conversation)
            if (c.branches.isEmpty()) {
                dispose()
                return@addListener
            }
            e.isCancelled = true

            launch {
                handleInput(c, player, e.rawMessage)
            }
        }

        startConvo(player, require(Conversation))

        onDispose {
            MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
            ongoingConversations.remove(player.uuid)
        }
    }

    private suspend fun startConvo(player: RoguelikePlayer, conversation: Conversation) {
        player.sendMessage(conversation.speakerName + ":".asComponent())
        player.sendMessage(conversation.prompt)

        for ((message, _) in conversation.branches) {
            player.sendMessage("<green>-> ".asComponent() + message)
            return
        }

        conversation.execute(player)
    }

    private suspend fun RenderContext.handleInput(conversation: Conversation, player: RoguelikePlayer, response: String) {
        val closestEntry = conversation.stringBranches.entries.associate {
            levenshtein(it.key, response)to it.value
        }.minBy { it.key }

        val closest = if (closestEntry.key > MAXIMUM_RESPONSE_DISTANCE) {
            player.sendMessage(conversation.speakerName + ":".asComponent())
            player.sendMessage(conversation.unknownResponse)
            dispose()
            return
        } else closestEntry.value

        set(Conversation, closest)
        startConvo(player, closest)
    }
}

data class Conversation(
    val speakerName: Component,
    val prompt: Component,
    val branches: Map<Component, Conversation>,
    val unknownResponse: Component,
    var execute: suspend (RoguelikePlayer) -> Unit = {}
) : RenderContext.Element {
    override val key: RenderContext.Key<*> = Companion

    val stringBranches: Map<String, Conversation> = branches.mapKeys { it.key.asString() }

    suspend fun start(player: RoguelikePlayer) {
        val eventNode = EventNode.event("roguelike:conversation", EventFilter.PLAYER) { it.player == player }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        try {
            eventNode.addListener(PlayerChatEvent::class.java) { e ->
                e.isCancelled = true
                val response = e.rawMessage
                val closestEntry = stringBranches.entries.associate {
                    levenshtein(it.key, response)to it.value
                }.minBy { it.key }
                MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
                val closest = if (closestEntry.key > MAXIMUM_RESPONSE_DISTANCE) {
                    player.sendMessage(speakerName + ":".asComponent())
                    player.sendMessage(unknownResponse)
                    return@addListener
                } else closestEntry.value
                Dispatchers.Default.launch {
                    closest.start(player)
                }
            }
            player.sendMessage(speakerName + ":".asComponent())
            player.sendMessage(prompt)
            for ((message, _) in branches) {
                player.sendMessage("<green>-> ".asComponent() + message)
                return
            }
            execute(player)
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        }
    }

    companion object : RenderContext.Key<Conversation> {
        const val MAXIMUM_RESPONSE_DISTANCE = 6
    }
}

@DslMarker
annotation class ConversationDsl

class ConversationBuilder(
    private var speakerName: Component
) {
    private var prompt: Component = Component.text("...")
    private var execute: suspend (RoguelikePlayer) -> Unit = {}
    private var branches: MutableMap<Component, Conversation> = HashMap()
    private var unknownResponse: Component = Component.text("I don't understand.")

    @ConversationDsl
    fun say(message: Component) {
        prompt = message
    }

    @ConversationDsl
    fun execute(block: suspend (RoguelikePlayer) -> Unit) {
        execute = block
    }

    @ConversationDsl
    fun response(newSpeaker: Component, message: Component, block: ConversationBuilder.() -> Unit) {
        val branch = ConversationBuilder(newSpeaker).apply(block).build()
        branches[message] = branch
    }

    @ConversationDsl
    fun response(message: Component, block: ConversationBuilder.() -> Unit) {
        val branch = ConversationBuilder(speakerName).apply(block).build()
        branches[message] = branch
    }

    @ConversationDsl
    fun unknownResponse(message: Component) {
        unknownResponse = message
    }

    fun build() : Conversation = Conversation(speakerName, prompt, branches, unknownResponse, execute)
}

@ConversationDsl
fun conversation(speaker: Component, block: ConversationBuilder.() -> Unit) = ConversationBuilder(speaker).apply(block).build()