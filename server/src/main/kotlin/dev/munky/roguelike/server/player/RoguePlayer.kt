package dev.munky.roguelike.server.player

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.common.renderdispatcherapi.RenderDispatch
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.death.DeathRenderer
import dev.munky.roguelike.server.instance.RogueInstance
import dev.munky.roguelike.server.interact.HoverableInteractableCreature
import dev.munky.roguelike.server.interact.InteractableRegion
import dev.munky.roguelike.server.interact.Region
import dev.munky.roguelike.server.item.RogueItem
import dev.munky.roguelike.server.item.WeaponData
import dev.munky.roguelike.server.item.Weapon
import dev.munky.roguelike.server.player.Character.Companion.WEAPON_SLOT
import kotlinx.serialization.Serializable
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.attribute.AttributeModifier
import net.minestom.server.entity.attribute.AttributeOperation
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * A player in the roguelike server.
 * For character-related data, refer to [CharacterData].
 */
class RoguePlayer(connection: PlayerConnection, profile: GameProfile) : Player(connection, profile), RenderContext.Element {
    override val key: RenderContext.Key<*> = Companion
    var isDebug = true

    var hoveredInteractable: HoverableInteractableCreature? = null

    /**
     * The area and the expanded shape for detecting exit.
     */
    val areasInside = ConcurrentHashMap<InteractableRegion, Region>()

    val account = Roguelike.server().accounts()[uuid.toString()] ?: AccountData.new(this)
    var character = Character(account.characters.first())

    val hotbar = Array<RogueItem?>(9) { null }

    init {
        getAttribute(Attribute.ENTITY_INTERACTION_RANGE).addModifier(DEFAULT_ENTITY_INTERACTION_MODIFIER)
    }

    fun refreshLoadout() {
        inventory.setItemStack(WEAPON_SLOT, character.weapon.createCustomItemStack())
        hotbar[WEAPON_SLOT] = character.weapon
    }

    override fun spawn() {
        areasInside.clear()
        refreshLoadout()
    }

    override fun kill() {
        val instance = instance
        if (instance == null || instance !is RogueInstance) {
            super.kill()
            return
        }
        if (!isDead) {
            with(instance) {
                RenderDispatch.with(DeathRenderer)
                    .with(this)
                    .with(this@RoguePlayer)
                    .dispatchManaged()
            }
        }
        isDead = true // so the super player class doesnt send the death screen.
        super.kill()
    }

    companion object : RenderContext.Key<RoguePlayer> {
        val DEFAULT_ENTITY_INTERACTION_MODIFIER = AttributeModifier("roguelike:default.entity_interaction_range", 3.0, AttributeOperation.ADD_VALUE)
    }
}

object RoguelikePlayers : RenderContext.Key<Collection<RoguePlayer>>

@Serializable
data class AccountData(
    val uuid: dev.munky.modelrenderer.skeleton.UUID,
    val lastUsername: String,
    val characters: Set<CharacterData>
) {
    companion object {
        fun new(player: RoguePlayer) = AccountData(player.uuid, player.username, hashSetOf(
            CharacterData(WeaponData(WeaponData.CombatStyle.SWORD, emptyMap()))
        ))
    }
}

@Serializable
data class CharacterData(
    val weaponData: WeaponData
)

data class Character(val data: CharacterData) {
    val weapon = Weapon(data.weaponData)

    fun withWeapon(weaponData: WeaponData) : Character = copy(data = data.copy(weaponData = weaponData))

    companion object {
        const val WEAPON_SLOT = 0
    }
}