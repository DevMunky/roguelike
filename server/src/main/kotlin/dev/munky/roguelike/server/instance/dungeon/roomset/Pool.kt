package dev.munky.roguelike.server.instance.dungeon.roomset

import dev.munky.roguelike.common.WeightedRandomList
import java.util.ArrayList
import java.util.HashMap

data class Pool (
    val id: String,
    val entries: WeightedRandomList<String>,
    val connectedPools: List<String>
) {
    fun isConnected(other: Pool) = connectedPools.contains(other.id) || other.connectedPools.contains(id)

    companion object {
        fun of(roomSet: RoomSet, id: String, data: PoolData) : Pool {
            val connections = arrayListOf(id)
            val rooms = HashMap<String, Double>()
            gatherRooms(roomSet.data.roomPools, rooms, data)
            gatherConnections(roomSet.data.roomPools, connections, data)
            return Pool(
                id,
                WeightedRandomList(rooms),
                connections
            )
        }

        private fun gatherConnections(pools: Map<String, PoolData>, connections: ArrayList<String>, pool: PoolData) {
            when (pool) {
                is ReferencePoolData -> gatherConnections(pools, connections, pools[pool.id]!!)
                is UnionPoolData -> pool.pools.forEach { gatherConnections(pools, connections, it) }

                is TerminalPoolData -> connections += pools.entries.first { it.value == pool }.key
            }
        }

        private fun gatherRooms(pools: Map<String, PoolData>, rooms: HashMap<String, Double>, pool: PoolData) {
            when (pool) {
                is ReferencePoolData -> gatherRooms(pools, rooms, pools[pool.id]!!)
                is UnionPoolData -> pool.pools.forEach { gatherRooms(pools, rooms, it) }

                is TerminalPoolData -> rooms.putAll(pool.rooms)
            }
        }
    }
}