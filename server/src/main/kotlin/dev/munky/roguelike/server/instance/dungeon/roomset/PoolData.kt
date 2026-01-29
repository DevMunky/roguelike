package dev.munky.roguelike.server.instance.dungeon.roomset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PoolData

@Serializable
@SerialName("reference")
data class ReferencePoolData(
    val id: String
) : PoolData

@Serializable
@SerialName("room_pool")
data class TerminalPoolData(
    val rooms: Map<String, Double>
) : PoolData

@Serializable
@SerialName("union_pool")
data class UnionPoolData(
    val pools: List<PoolData>
) : PoolData