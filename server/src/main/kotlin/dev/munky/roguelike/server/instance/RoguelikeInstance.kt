package dev.munky.roguelike.server.instance

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.InteractableArea
import dev.munky.roguelike.server.interact.InteractableAreaContainer
import dev.munky.roguelike.server.player.RoguelikePlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import java.util.*

abstract class RoguelikeInstance(
    uuid: UUID,
    dimensionType: RegistryKey<DimensionType>
) : InstanceContainer(uuid, dimensionType), InteractableAreaContainer, RenderContext.Element {
    final override val key: RenderContext.Key<*> = Companion

    override val areas: HashSet<InteractableArea> = HashSet()

    override fun createArea(b: InteractableArea.Dsl.() -> Unit) {
        areas.add(InteractableArea.area(b))
    }

    open fun onEnter(player: RoguelikePlayer) {}
    open fun onExit(player: RoguelikePlayer) {}

    companion object : RenderContext.Key<RoguelikeInstance> {
        val EVENT_NODE: EventNode<InstanceEvent> = EventNode.type("${Roguelike.NAMESPACE}:instance", EventFilter.INSTANCE)

        fun initialize() {
            EVENT_NODE.addListener(AddEntityToInstanceEvent::class.java) {
                val p = it.entity as? RoguelikePlayer ?: return@addListener
                val i = it.instance as? RoguelikeInstance ?: return@addListener
                i.scheduleNextTick {
                    i.onEnter(p)
                }
            }
            EVENT_NODE.addListener(RemoveEntityFromInstanceEvent::class.java) {
                val p = it.entity as? RoguelikePlayer ?: return@addListener
                val i = it.instance as? RoguelikeInstance ?: return@addListener
                i.scheduleNextTick {
                    i.onExit(p)
                }
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}