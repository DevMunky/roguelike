package dev.munky.roguelike.server.interact

import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType

/**
 * No gravity by default.
 */
abstract class InteractableCreature(type: EntityType) : EntityCreature(type), Interactable {
    init {
        setNoGravity(true)
    }
}