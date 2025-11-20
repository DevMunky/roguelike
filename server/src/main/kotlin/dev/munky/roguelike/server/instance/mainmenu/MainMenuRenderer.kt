package dev.munky.roguelike.server.instance.mainmenu

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.Renderer
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.raycast.Ray
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent

object MainMenuRenderer : Renderer {


    override suspend fun RenderContext.render() {
        val player = require(RenderKey.Player)
        val eventNode = EventNode.event("${Roguelike.NAMESPACE}:main_menu_renderer.${player.username}", EventFilter.PLAYER) { it.player == player }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)

        val selectCharacterEntity = Entity(EntityType.VILLAGER)
        val createCharacterEntity = Entity(EntityType.VILLAGER)

        selectCharacterEntity.setInstance(player.instance!!, player.position)
        createCharacterEntity.setInstance(player.instance!!, player.position)

        eventNode.addListener(PlayerMoveEvent::class.java) {
            val player = it.player
            val dir = player.position.direction()
            val ray = Ray(player.position.withY { y -> y + player.eyeHeight }, dir)
            val hovering = ray.entitiesSorted(listOf(selectCharacterEntity, createCharacterEntity)).firstOrNull() ?: return@addListener
            set(HoveredOption, when (hovering.value) {
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
            selectCharacterEntity.isInvisible = it != Option.NONE
            selectCharacterEntity.isInvisible = it != Option.NONE
            when (it) {
                Option.SELECT_CHARACTER -> {

                }
                Option.CREATE_CHARACTER -> {

                }
                Option.NONE -> {
                }
            }
        }

        watchAndRequire(HoveredOption) {
            createCharacterEntity.isGlowing = it == Option.CREATE_CHARACTER
            selectCharacterEntity.isGlowing = it == Option.SELECT_CHARACTER
        }

        onDispose {
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