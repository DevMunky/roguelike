package dev.munky.roguelike.server.instance.dungeon.roomset.feature

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction

sealed interface JigsawData {
    val name: String
    val poolName: String
    val finalBlock: Block
    /**
     * Used for identification of different features
     */
    val target: String

    val position: BlockVec
    val direction: Direction

    companion object {
        fun getAlignedPosition(origin: BlockVec, from: JigsawData, to: JigsawData): BlockVec {
            val connectorPos = from.position.add(origin)
            val otherPos = connectorPos.add(
                from.direction.normalX(),
                from.direction.normalY(),
                from.direction.normalZ()
            )
            return otherPos.sub(to.position)
        }
    }
}