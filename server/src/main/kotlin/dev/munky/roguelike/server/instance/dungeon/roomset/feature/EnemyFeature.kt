package dev.munky.roguelike.server.instance.dungeon.roomset.feature

import dev.munky.roguelike.server.Roguelike
import dev.munky.roguelike.server.instance.dungeon.roomset.Pool
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction

data class EnemyFeature(
    override val name: String,
    override val poolName: String,
    val pool: Pool?,
    override val finalBlock: Block,

    override val position: BlockVec,
    override val direction: Direction
) : JigsawData {
    override val target: String get() = ID

    companion object {
        const val ID = "${Roguelike.NAMESPACE}:enemy"
    }
}