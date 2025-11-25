package dev.munky.roguelike.server.instance

import dev.munky.roguelike.server.interact.InteractableArea
import dev.munky.roguelike.server.interact.InteractableAreaContainer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import java.util.UUID

abstract class RoguelikeInstance(
    uuid: UUID,
    dimensionType: RegistryKey<DimensionType>
) : InstanceContainer(uuid, dimensionType), InteractableAreaContainer {
    override val areas: HashSet<InteractableArea> = HashSet()

    override fun createArea(b: InteractableArea.Dsl.() -> Unit) {
        areas.add(InteractableArea.area(b))
    }
}