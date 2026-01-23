package dev.munky.roguelike.server.command

import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.ArgumentCallback
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.suggestion.SuggestionCallback
import net.minestom.server.entity.Player

@DslMarker
annotation class CommandMarker

@CommandMarker
interface CommandBranch {

    fun suggest(entry: SuggestionCallback)

    /**
     * Adds a literal argument named [name]
     */
    fun argument(name: String, block: CommandBranch.() -> Unit)

    /**
     * Adds all of [args]
     */
    fun argument(vararg args: Pair<Argument<*>, ArgumentCallback?>, block: CommandBranch.() -> Unit)

    /**
     * Adds all of [args]
     */
    fun argument(vararg args: Argument<*>, block: CommandBranch.() -> Unit)

    /**
     * Creates an executor at the current point in the hierarchy.
     */
    fun executor(block: suspend (sender: CommandSender, ctx: CommandContext) -> Unit)
}

inline fun CommandBranch.playerExecutor(crossinline block: suspend (sender: Player, ctx: CommandContext) -> Unit) =
    executor { s, c -> if (s is Player) block(s, c) }

/**
 * Create a command.
 */
fun command(name: String, block: CommandBranch.() -> Unit): Command = CommandDSLImpl.createCommand(name, block)