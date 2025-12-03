package dev.munky.roguelike.server.interact

import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.angle
import dev.munky.roguelike.server.player.RoguePlayer
import net.minestom.server.entity.EntityType
import org.joml.Math.toRadians
import java.util.*

interface TalkingInteractable : Interactable {
    val conversation: Conversation

    fun onDialogueEnd(player: RoguePlayer, endState: ConversationRenderer.EndState) {}
    fun onDialogueStart(player: RoguePlayer) {}

    fun RenderDispatch.buildDispatch() {}

    override fun onInteract(player: RoguePlayer) {
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

abstract class TalkingInteractableCreature(type: EntityType) : HoverableInteractableCreature(type), TalkingInteractable {
    var targetPlayers: Stack<RoguePlayer> = Stack()
    var preTargetYaw = 0f
    var preTargetPitch = 0f

    override fun onDialogueStart(player: RoguePlayer) = engage(player)

    // This means once the player is already hovering on this, it will not engage until they stop hovering
    // This is because hoverStart is not called again regardless of what happens here.
    override fun onHoverStart(player: RoguePlayer) = engage(player, 180f)

    override fun onDialogueEnd(player: RoguePlayer, endState: ConversationRenderer.EndState) = disengage()
    override fun onHoverEnd(player: RoguePlayer) = disengage()

    fun engage(player: RoguePlayer, fieldOfView: Float = 360f) {
        if (targetPlayers.empty()) {
            preTargetPitch = position.pitch
            preTargetYaw = position.yaw
        }
        val ptm = player.position.sub(position)
        val dir = position.direction()
        if (ptm.angle(dir) < toRadians(fieldOfView / 2.0))
            targetPlayers.push(player)
    }

    fun disengage() {
        if (!targetPlayers.empty()) targetPlayers.pop()
        if (targetPlayers.empty()) setView(preTargetYaw, preTargetPitch)
    }

    override fun RenderDispatch.buildDispatch() {
        with(ConversationRenderer.SpeakerOrigin, position)
        with(ConversationRenderer.InterruptRange, range)
    }

    override fun tick(time: Long) {
        if (targetPlayers.empty()) {
            super.tick(time)
            return
        }
        lookAt(targetPlayers.peek())
        super.tick(time)
    }
}