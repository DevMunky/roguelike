package dev.munky.roguelike.server

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.hollowcube.schem.util.Rotation
import net.minestom.server.coordinate.CoordConversion
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.ServerPacket
import net.minestom.server.network.packet.server.play.BundlePacket
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

fun Point.toJoml() = org.joml.Vector3d(x(), y(), z())

suspend fun Instance.loadChunksInGlobal(rx: Pair<Int, Int>, rz: Pair<Int, Int>) = loadChunksInChunks(
    CoordConversion.globalToChunk(rx.first) to CoordConversion.globalToChunk(rx.second),
    CoordConversion.globalToChunk(rz.first) to CoordConversion.globalToChunk(rz.second),
)

fun Instance.sendGroupedPacketBundle(packets: Collection<ServerPacket>) {
    val bundle = BundlePacket()
    sendGroupedPacket(bundle)
    for (packet in packets) {
        sendGroupedPacket(packet)
    }
    sendGroupedPacket(bundle)
}

fun Vec.randomPerpendicular(rng: Random): Vec {
    // Pick any non-parallel vector
    val arbitrary: Vec = if (abs(y) < 0.9) Vec(.0, 1.0, .0)
    else Vec(1.0, .0, .0)

    val perp: Vec = cross(arbitrary).normalize()
    val perp2: Vec = cross(perp).normalize()

    val a: Double = rng.nextDouble() * 2 - 1
    val b: Double = rng.nextDouble() * 2 - 1

    return perp.mul(a).add(perp2.mul(b)).normalize()
}

suspend fun Instance.loadChunksInChunks(rx: Pair<Int, Int>, rz: Pair<Int, Int>) = coroutineScope {
    val x1 = max(rx.first, rx.second)
    val z1 = max(rz.first, rz.second)
    val x2 = min(rx.first, rx.second)
    val z2 = min(rz.first, rz.second)
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