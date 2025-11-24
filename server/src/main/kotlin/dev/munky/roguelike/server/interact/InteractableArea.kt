package dev.munky.roguelike.server.interact

import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.collision.BoundingBox
import java.time.Duration

interface InteractableArea : Interactable {
    val area: BoundingBox

    /**
     * The thickness of the [area] so that the player
     * has a little buffer between exiting after entry.
     */
    val thickness: Double

    /**
     * The time it between entering the area and [onEnter] being called.
     */
    val bufferTime: Duration

    fun onEnter(player: RoguelikePlayer)

    override fun onInteract(player: RoguelikePlayer) {
        TODO("Not yet implemented")
    }
}