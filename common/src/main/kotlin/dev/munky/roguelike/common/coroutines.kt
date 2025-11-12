package dev.munky.roguelike.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

fun CoroutineContext.launch(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.()->Unit
) = CoroutineScope(this).launch(start = start, block = block)

fun <T> CoroutineContext.async(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.()->T
) = CoroutineScope(this).async(start = start, block = block)