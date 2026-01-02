package dev.munky.roguelike.server.instance

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandle
import dev.munky.roguelike.common.renderdispatcherapi.RenderHandleManager
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.InteractableRegion
import dev.munky.roguelike.server.interact.InteractableRegionContainer
import dev.munky.roguelike.server.player.RoguePlayer
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

abstract class RogueInstance(
    uuid: UUID,
    dimensionType: RegistryKey<DimensionType>
) : InstanceContainer(uuid, dimensionType), InteractableRegionContainer, RenderContext.Element, RenderHandleManager {
    final override val key: RenderContext.Key<*> = Companion

    override val areas: HashSet<InteractableRegion> = HashSet()
    val regionRenderHandles = HashMap<RoguePlayer, HashMap<Renderer, RenderHandle>>()

    override fun createArea(b: InteractableRegion.Dsl.() -> Unit) {
        areas.add(InteractableRegion.Dsl().apply(b).build())
    }

    open fun onEnter(player: RoguePlayer) {}
    open fun onExit(player: RoguePlayer) {
        player.areasInside.clear()
        regionRenderHandles.remove(player)?.forEach { it.value.dispose() }
    }

    override fun RenderDispatch.dispatchManaged() {
        val player = data[RoguePlayer] as? RoguePlayer ?: error("Cannot dispatchManaged without a player.")
        val handles = regionRenderHandles.getOrPut(player) { HashMap() }
        handles[data[Renderer]!! as Renderer] = dispatch()
    }

    companion object : RenderContext.Key<RogueInstance> {
        private val EVENT_NODE: EventNode<InstanceEvent> = EventNode.type("${Roguelike.NAMESPACE}:instance", EventFilter.INSTANCE)

        fun initialize() {
            EVENT_NODE.addListener(AddEntityToInstanceEvent::class.java) {
                val p = it.entity as? RoguePlayer ?: return@addListener
                val i = it.instance as? RogueInstance ?: return@addListener
                i.scheduleNextTick {
                    i.onEnter(p)
                }
            }
            EVENT_NODE.addListener(RemoveEntityFromInstanceEvent::class.java) {
                val p = it.entity as? RoguePlayer ?: return@addListener
                val i = it.instance as? RogueInstance ?: return@addListener
                i.scheduleNextTick {
                    i.onExit(p)
                }
            }
            MinecraftServer.getGlobalEventHandler().addChild(EVENT_NODE)
        }
    }
}