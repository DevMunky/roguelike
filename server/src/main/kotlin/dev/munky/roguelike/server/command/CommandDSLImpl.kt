package dev.munky.roguelike.server.command

import dev.munky.roguelike.server.Roguelike
import dev.munky.tenebris.core.command.CommandBranch
import dev.munky.roguelike.server.asComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.ArgumentCallback
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.CommandExecutor
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.arguments.ArgumentLiteral
import java.util.*
import kotlin.collections.ArrayList

internal object CommandDSLImpl {
    private val MISSING_EXECUTOR = CommandExecutor { sender, context ->
        sender.sendMessage("<red>Unknown command '${context.input}' (${context.map})".asComponent())
    }

    fun createCommand(name: String, block: CommandBranch.() -> Unit): Command {
        val command = RootBranchImpl(name)
        command.block()
        return command.build()
    }

    private abstract class AbstractCommandBranch: CommandBranch {
        val children = ArrayList<CommandBranchImpl>()
        var executor: CommandExecutor? = null

        override fun add(name: String, block: CommandBranch.() -> Unit) {
            val branch = CommandBranchImpl(ArgumentLiteral(name))
            branch.block()
            children += branch
        }

        override fun add(vararg args: Pair<Argument<*>, ArgumentCallback?>, block: CommandBranch.() -> Unit) {
            val mapped = args.map { it.first.apply { callback = it.second } }.toTypedArray()
            val argBranch = CommandBranchImpl(*mapped)
            argBranch.block()
            children += argBranch
        }

        override fun add(vararg args: Argument<*>, block: CommandBranch.() -> Unit) {
            val argBranch = CommandBranchImpl(*args)
            argBranch.block()
            children += argBranch
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        override fun executor(block: suspend (sender: CommandSender, ctx: CommandContext) -> Unit) {
            executor = CommandExecutor {s, c ->
                scope.launch {
                    try {
                        block(s, c)
                    } catch (t: Throwable) {
                        Roguelike.server().process().exception().handleException(t)
                        s.sendMessage("<red>Error while running that command".asComponent())
                    }
                }
            }
        }

        abstract fun build(parent: Command)
    }

    private class RootBranchImpl(val name: String): AbstractCommandBranch() {
        fun build(): Command {
            val cmd = Command(name)
            for (child in children) {
                child.build(cmd)
            }
            cmd.defaultExecutor = executor ?: MISSING_EXECUTOR
            return cmd
        }

        override fun build(parent: Command) {
            parent.addSubcommand(build())
        }
    }

    // Maybe change this to two separate arrays, one for arguments and one for the callbacks
    private class CommandBranchImpl(vararg val args: Argument<*>): AbstractCommandBranch() {
        override fun build(parent: Command) {
            if (executor != null) parent.addSyntax(executor ?: MISSING_EXECUTOR, *args)
            for (child in children) {
                child.buildWithPrevious(args, parent)
            }
        }

        fun buildWithPrevious(previous: Array<out Argument<*>>, parent: Command) {
            val marg= previous + args
            if (executor != null) parent.addSyntax(executor ?: MISSING_EXECUTOR, *marg)
            for (child in children) {
                child.buildWithPrevious(marg, parent)
            }
        }
    }
}

// why do i have to do this
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private operator fun <T> Array<out T>.plus(elements: Array<out T>): Array<out T> {
    val thisSize = size
    val arraySize = elements.size
    val result = Arrays.copyOf(this, thisSize + arraySize)
    System.arraycopy(elements, 0, result, thisSize, arraySize)
    return result
}