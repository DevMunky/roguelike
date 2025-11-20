package dev.munky.roguelike.server.player

import dev.munky.roguelike.server.Roguelike
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.minestom.server.entity.Player
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * A player in the roguelike server.
 * For character-related data, refer to [Character].
 */
class RoguelikePlayer(connection: PlayerConnection, profile: GameProfile) : Player(connection, profile) {
    val character = Character(Character.CombatStyle.SWORD)

    /**
     * Loads account data associated with this player's uuid.
     */
    fun loadAccount() : AccountData {
        val acc = Roguelike.server().accounts()[Key.key("account_store", uuid.toString())]
            ?: AccountData(username, HashSet())
        return acc
    }
}

@Serializable
data class AccountData(
    val lastUsername: String,
    val characters: Set<Character>
)

@Serializable
data class Character(
    val combat: CombatStyle
) {
    enum class CombatStyle { SWORD, SPELL }
}