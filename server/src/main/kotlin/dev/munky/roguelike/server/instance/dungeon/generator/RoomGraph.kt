package dev.munky.roguelike.server.instance.dungeon.generator

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class Tree<T: Any>(
    root: T,
    map: ()->MutableMap<T, Node> = ::ConcurrentHashMap,
) {
    @PublishedApi
    internal val nodes = map()

    val root: Node

    init {
        require(nodes.isEmpty()) { "Map must be empty to create a new tree." }

        this.root = Node(null, root, newChildrenSet())
        nodes[root] = this.root
    }

    fun getNode(value: T): Node? = nodes[value]

    /**
     * Adds a new node as a child of [parent].
     *
     * @return the newly created child node.
     */
    fun addNode(parent: Node, child: T): Node = Node(parent, child, newChildrenSet()).also {
        parent.children.add(it)
        nodes[child] = it
    }

    /**
     * Clears every node _except_ the root.
     */
    fun clear() {
        nodes.clear()
        nodes[root.value] = root
    }

    /**
     * Removes the node and all of its children recursively.
     */
    fun removeNode(node: Node) {
        // Detach from parent first to keep the tree consistent
        node.parent?.children?.remove(node)

        // Remove this node and all descendants from the index map
        nodes.remove(node.value)

        val childrenCopy = node.children
        for (child in childrenCopy) {
            removeNode(child)
        }
        node.children.clear()
    }

    fun <S : Any> map(transform: (parent: T?, value: T) -> S) : Tree<S> {
        val newTree = Tree(transform(null, root.value))

        for (child in root.children) {
            recursiveMap(newTree, newTree.root, child, transform)
        }

        return newTree
    }

    private fun <S: Any> recursiveMap(tree: Tree<S>, parent: Tree<S>.Node, node: Node, transform: (T?, T)->S) {
        val children = node.children
        val newNode = tree.addNode(parent, transform(node.value, node.value))
        for (child in children) {
            val new = transform(node.value, child.value)
            tree.addNode(newNode, new)
            recursiveMap(tree, newNode, child, transform)
        }
    }

    suspend fun <S : Any> mapAsync(transform: suspend T.(parent: T?) -> S) : Tree<S> {
        val newTree = Tree(transform(root.value, null))

        for (child in root.children) {
            recursiveMapAsync(newTree, newTree.root, child, transform)
        }

        return newTree
    }

    private suspend fun <S: Any> recursiveMapAsync(tree: Tree<S>, parent: Tree<S>.Node, node: Node, transform: suspend T.(parent: T?) -> S) {
        val children = node.children
        val newNode = tree.addNode(parent, transform(node.value, node.parent?.value))
        coroutineScope {
            for (child in children) {
                val new = transform(child.value, node.value)
                tree.addNode(newNode, new)
                launch { recursiveMapAsync(tree, newNode, child, transform) }
            }
        }
    }

    inner class Node(
        val parent: Node?,
        val value: T,
        val children: MutableSet<Node>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Tree<T>.Node

            return value == other.value
        }

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Tree.Node(parent=${parent?.value}, value=$value, children=$children)"
    }

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun <T: Any> empty() : Tree<T> = Tree(Unit as T)

        private fun <T: Any> newChildrenSet() = ConcurrentHashMap.newKeySet<Tree<T>.Node>()
    }
}