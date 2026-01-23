package dev.munky.roguelike.server.instance.dungeon.generator

class Tree<T: Any> {
    private val rootNode: Node? = null



    inner class Node(
        val parent: Node?,
        val value: T,
        val children: ArrayList<Node>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Tree<T>.Node

            return value == other.value
        }

        override fun hashCode(): Int = value.hashCode()
    }
}