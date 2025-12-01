package dev.munky.roguelike.server.player

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.InteractableArea
import dev.munky.roguelike.server.item.ModifierFlameBurst
import dev.munky.roguelike.server.item.Weapon
import dev.munky.roguelike.server.item.WeaponInstance
import kotlinx.serialization.Serializable
import net.minestom.server.entity.Player
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection

/**
 * A player in the roguelike server.
 * For character-related data, refer to [Character].
 */
class RoguelikePlayer(connection: PlayerConnection, profile: GameProfile) : Player(connection, profile), RenderContext.Element {
    override val key: RenderContext.Key<*> = Companion

    var isDebug = true

    val account = Roguelike.server().accounts()[uuid.toString()] ?: AccountData.new(this)

    var currentCharacter = CharacterInstance(account.characters.first())

    val areasInside = HashSet<InteractableArea>()

    override fun spawn() {
        areasInside.clear()
        val item = currentCharacter.weapon.buildItem()
        inventory.setItemStack(0, item)
    }

    companion object : RenderContext.Key<RoguelikePlayer>
}

object RoguelikePlayers : RenderContext.Key<Collection<RoguelikePlayer>>

@Serializable
data class AccountData(
    val lastUsername: String,
    val characters: Set<Character>
) {
    companion object {
        fun new(player: RoguelikePlayer) = AccountData(player.username, hashSetOf(
            Character(Weapon(Weapon.CombatStyle.SWORD, setOf(ModifierFlameBurst(1))))
        ))
    }
}

@Serializable
data class Character(
    val weapon: Weapon
) {

}

class CharacterInstance(val data: Character) {
    val weapon = WeaponInstance(data.weapon)
}