package dev.munky.roguelike.server.instance.mainmenu

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.player.RoguePlayer
import dev.munky.roguelike.server.raycast.Ray
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.attribute.AttributeModifier
import net.minestom.server.entity.attribute.AttributeOperation
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent

object MainMenuRenderer : Renderer {
    private val INTERACT_ATTR = AttributeModifier("${Roguelike.NAMESPACE}:main_menu_interact", 10.0, AttributeOperation.ADD_VALUE)
    private val SPEED_ATTR = AttributeModifier("${Roguelike.NAMESPACE}:main_menu_speed", .001, AttributeOperation.ADD_MULTIPLIED_TOTAL)

    override suspend fun RenderContext.render() {
        val player = require(RoguePlayer)
        val mainMenu = player.instance as? MainMenuInstance ?: return

        val eventNode = EventNode.event("${Roguelike.NAMESPACE}:main_menu_renderer.${player.username}", EventFilter.PLAYER) { it.player == player }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        player.getAttribute(Attribute.fromKey("entity_interaction_range")).addModifier(INTERACT_ATTR)
        player.getAttribute(Attribute.fromKey("movement_speed")).addModifier(SPEED_ATTR)

        val selectCharacterEntity = Entity(EntityType.VILLAGER)
        val createCharacterEntity = Entity(EntityType.VILLAGER)

        selectCharacterEntity.setInstance(mainMenu, player.position.add(-2.0, .0, 2.0).withYaw(-180f))
        createCharacterEntity.setInstance(mainMenu, player.position.add(2.0, .0,  2.0).withYaw(-180f))

        eventNode.addListener(PlayerMoveEvent::class.java) {
            val player = it.player
            val dir = player.position.direction().mul(10.0)
            val ray = Ray(player.position.withY { y -> y + player.eyeHeight }, dir)
            val hovering = ray.entitiesSorted(listOf(selectCharacterEntity, createCharacterEntity)).firstOrNull()
            set(HoveredOption, when (hovering?.value) {
                selectCharacterEntity -> Option.SELECT_CHARACTER
                createCharacterEntity -> Option.CREATE_CHARACTER
                else -> Option.NONE
            })
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) {
            set(SelectedOption, when (it.target) {
                selectCharacterEntity -> Option.SELECT_CHARACTER
                createCharacterEntity -> Option.CREATE_CHARACTER
                else -> Option.NONE
            })
        }

        watchAndRequire(SelectedOption) {
            when (it) {
                Option.SELECT_CHARACTER -> {
                    player.setInstance(Roguelike.server().instanceManager().town)
                    dispose()
                }
                Option.CREATE_CHARACTER -> {
                    player.sendMessage("Not implemented.")
                }
                Option.NONE -> {
                    player.sendMessage("None character")
                }
            }
        }

        watchAndRequire(HoveredOption) {
            createCharacterEntity.isGlowing = it == Option.CREATE_CHARACTER
            selectCharacterEntity.isGlowing = it == Option.SELECT_CHARACTER
        }

        onDispose {
            player.getAttribute(Attribute.fromKey("entity_interaction_range")).removeModifier(INTERACT_ATTR)
            player.getAttribute(Attribute.fromKey("movement_speed")).removeModifier(SPEED_ATTR)

            createCharacterEntity.remove()
            selectCharacterEntity.remove()
            MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        }
    }

    data object HoveredOption : RenderContext.StableKey<Option> {
        override val default: Option = Option.NONE
    }

    data object SelectedOption : RenderContext.Key<Option>

    enum class Option  {
        SELECT_CHARACTER,
        CREATE_CHARACTER,
        NONE
    }
}