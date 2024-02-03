package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data

import javafx.geometry.Rectangle2D
import javafx.scene.Node
import java.util.*

data class DragLayoutLeaf(
    var component: Node? = null,
    var node: DragLayoutNode? = null,
    var id: String = UUID.randomUUID().toString(),
) {

    var boundCache: Rectangle2D? = null

    init {
        if (isComponent && isNode) throw IllegalArgumentException("The leaf can only contain one type")
        if (!isComponent && !isNode) throw IllegalArgumentException("The leaf must contain exactly one type")
    }

    fun swapOnto(other: DragLayoutLeaf) {
        val tempNode = other.node
        val tempComp = other.component
        val tempId = other.id
        other.node = node
        other.component = component
        other.id = id
        id = tempId
        node = tempNode
        component = tempComp
    }

    val isComponent: Boolean
        get() = component != null

    val isNode: Boolean
        get() = node != null
}