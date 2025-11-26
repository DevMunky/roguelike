package dev.munky.roguelike.server.player

import dev.munky.roguelike.common.renderdispatcherapi.RenderContext
import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.interact.InteractableArea
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

    val account = Roguelike.server().accounts()[uuid.toString()] ?: AccountData(username, HashSet())
    var currentCharacter = account.characters.firstOrNull()

    val areasInside = HashSet<InteractableArea>()

    override fun spawn() {
        areasInside.clear()
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
        val EMPTY = AccountData("", HashSet())
    }
}

@Serializable
data class Character(
    val combat: CombatStyle
) {
    enum class CombatStyle { SWORD, SPELL }
}