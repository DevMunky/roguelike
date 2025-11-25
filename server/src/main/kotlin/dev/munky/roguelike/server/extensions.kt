package dev.munky.roguelike.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec

fun Point.toJoml() = org.joml.Vector3d(x(), y(), z())