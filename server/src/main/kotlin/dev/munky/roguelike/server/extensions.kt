package dev.munky.roguelike.server

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.hollowcube.schem.util.CoordinateUtil
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.max
import kotlin.math.min

fun Point.toJoml() = org.joml.Vector3d(x(), y(), z())

suspend fun Instance.loadChunksInGlobal(rx: Pair<Int, Int>, rz: Pair<Int, Int>) = loadChunksInChunks(
    CoordConversion.globalToChunk(rx.first) to CoordConversion.globalToChunk(rx.second),
    CoordConversion.globalToChunk(rz.first) to CoordConversion.globalToChunk(rz.second),
)

suspend fun Instance.loadChunksInChunks(rx: Pair<Int, Int>, rz: Pair<Int, Int>) = coroutineScope {
    val x1 = max(rx.first, rx.second)
    val z1 = max(rz.first, rz.second)
    val x2 = min(rx.first, rx.second)
    val z2 = min(rz.first, rz.second)
    println("loading chunks from $x1 $z1 to $x2 $z2")
    for (x in x2..x1) {
        for (z in z2..z1) {
            launch { loadChunk(x, z).await() }
        }
    }
}

fun Point.angle(other: Point) = toJoml().angle(other.toJoml())

fun Point.rotateCenter(rotation: Rotation): Point = when (rotation) {
    Rotation.NONE -> this

    Rotation.CLOCKWISE_90 ->
        Vec(-z(), y(), x())

    Rotation.CLOCKWISE_180 ->
        Vec(-x(), y(), -z())

    Rotation.CLOCKWISE_270 ->
        Vec(z(), y(), -x())
}

/**
 * Rotates `orientation` as well as `facing` and fence properties.
 */
fun Block.rotate(rotation: Rotation): Block {
    var block = CoordinateUtil.rotateBlock(this, rotation)
    block.getProperty("orientation")?.let { block = rotateOrientation(block, it, rotation) }
    return block
}

private fun rotateOrientation(block: Block, orientation: String, rotation: Rotation): Block = when (rotation) {
    Rotation.NONE -> block
    Rotation.CLOCKWISE_90 -> block.withProperty(
        "orientation",
        orientation.rotateOrientation90()
    )
    Rotation.CLOCKWISE_180 -> block.withProperty(
        "orientation",
        orientation.rotateOrientation90().rotateOrientation90()
    )
    Rotation.CLOCKWISE_270 -> block.withProperty(
        "orientation",
        orientation.rotateOrientation90().rotateOrientation90().rotateOrientation90()
    )
}

private fun String?.rotateOrientation90() = when(this) {
    "north_up" -> "east_up"
    "south_up" -> "west_up"
    "east_up" -> "south_up"
    "west_up" -> "north_up"
    else -> this
}