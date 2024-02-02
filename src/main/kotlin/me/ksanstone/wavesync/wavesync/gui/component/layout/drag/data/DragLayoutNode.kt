package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data

import javafx.geometry.Orientation
import javafx.scene.Node

data class DragLayoutNode(
    val id: String,
    val orientation: Orientation = Orientation.HORIZONTAL,
    val children: MutableList<DragLayoutLeaf> = mutableListOf(),
    val dividers: MutableList<Double> = mutableListOf()
)

data class DragLayoutLeaf(
    val component: Node?,
    val node: DragLayoutNode?
) {

    init {
        if (isComponent && isNode) throw IllegalArgumentException("The leaf can only contain one type")
        if (!isComponent && !isNode) throw IllegalArgumentException("The leaf must contain exactly one type")
    }

    val isComponent: Boolean
        get() = component != null

    val isNode: Boolean
        get() = node != null
}