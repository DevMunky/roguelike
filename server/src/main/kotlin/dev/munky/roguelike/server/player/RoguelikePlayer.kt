package dev.munky.roguelike.server.player

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.server.RenderKey
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.Conversation
import kotlinx.serialization.Serializable
import net.minestom.server.entity.Player
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection

/**
 * A player in the roguelike server.
 * For character-related data, refer to [Character].
 */
class RoguelikePlayer(connection: PlayerConnection, profile: GameProfile) : Player(connection, profile), RenderContext.Element {
    override val key: RenderContext.Key<*> = RenderKey.Player

    val account = Roguelike.server().accounts()[uuid.toString()] ?: AccountData(username, HashSet())
    var currentCharacter = account.characters.firstOrNull()

}

@Serializable
data class AccountData(
    val lastUsername: String,
    val characters: Set<Character>
) {
    companion object {
        val EMPTY = AccountData("", HashSet())
    }
}

@Serializable
data class Character(
    val combat: CombatStyle
) {
    enum class CombatStyle { SWORD, SPELL }
}