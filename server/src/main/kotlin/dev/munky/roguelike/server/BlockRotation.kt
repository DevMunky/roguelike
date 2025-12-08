package dev.munky.roguelike.server

import net.hollowcube.schem.util.CoordinateUtil
import net.hollowcube.schem.util.Rotation
import net.minestom.server.instance.block.Block

/**
 * Rotates `orientation` as well as `facing` and fence properties.
 */
fun Block.rotate(rotation: Rotation): Block {
    // fences and stairs ('facing', 'north', 'south', 'east', 'west')
    var block = CoordinateUtil.rotateBlock(this, rotation)
    // logs
    block = block.rotateProperty("axis", rotation, ::rotateAxis90)
    // banners and signs,
    block = block.rotateProperty("rotation", rotation, ::rotateRotation90)
    // rails
    block = block.rotateProperty("shape", rotation, ::rotateShape90)
    // ONLY jigsaw and crafter
    block = block.rotateProperty("orientation", rotation, ::rotateOrientation90)
    return block
}

private inline fun Block.rotateProperty(name: String, rotation: Rotation, rotate90: String.()->String) : Block = getProperty(name)?.let { prop ->
    when (rotation) {
        Rotation.NONE -> this
        Rotation.CLOCKWISE_90 -> withProperty(name, prop.rotate90())
        Rotation.CLOCKWISE_180 -> withProperty(name, prop.rotate90().rotate90())
        Rotation.CLOCKWISE_270 -> withProperty(name, prop.rotate90().rotate90().rotate90())
    }
} ?: this

private fun rotateShape90(prop: String): String = when(prop) {
    "ascending_north" -> "ascending_east"
    "ascending_south" -> "ascending_west"
    "ascending_east" -> "ascending_south"
    "ascending_west" -> "ascending_north"

    "east_west" -> "north_south"
    "north_south" -> "east_west"

    "north_east" -> "north_west"
    "north_west" -> "south_west"
    "south_east" -> "north_east"
    "south_west" -> "south_east"
    else -> prop
}

private fun rotateRotation90(prop: String): String = prop.toIntOrNull()
    ?.plus(4)?.mod(16)?.toString() ?: prop

private fun rotateAxis90(prop: String): String = when (prop) {
    "x" -> "z"
    "z" -> "x"
    else -> prop
}

private fun rotateOrientation90(prop: String) = when(prop) {
    "north_up" -> "east_up"
    "south_up" -> "west_up"
    "east_up" -> "south_up"
    "west_up" -> "north_up"
    else -> prop
}