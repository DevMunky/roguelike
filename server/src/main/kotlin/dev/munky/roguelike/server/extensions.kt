package dev.munky.roguelike.server

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.Instance

fun Point.toJoml() = org.joml.Vector3d(x(), y(), z())

suspend fun Instance.loadChunksInGlobal(rx: IntRange, rz: IntRange) = loadChunksInChunks(
    CoordConversion.globalToChunk(rx.first)..CoordConversion.globalToChunk(rx.last),
    CoordConversion.globalToChunk(rz.first)..CoordConversion.globalToChunk(rz.last),
)

suspend fun Instance.loadChunksInChunks(rx: IntRange, rz: IntRange) = coroutineScope {
    for (x in rx) {
        for (z in rz) {
            launch {
                loadChunk(x, z).await()
            }
        }
    }
}

fun Point.angle(other: Point) = toJoml().angle(other.toJoml())