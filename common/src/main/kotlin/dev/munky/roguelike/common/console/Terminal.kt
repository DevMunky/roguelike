package dev.munky.roguelike.common.console

import dev.munky.roguelike.common.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

abstract class Terminal {
    fun start() = Dispatchers.IO.launch { next() }

    suspend fun CoroutineScope.next() {
        while (isActive) {
            yield()
            parse(readlnOrNull() ?: continue)
        }
    }

    abstract fun parse(line: String)
}