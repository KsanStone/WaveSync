package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data

import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.geometry.Side
import javafx.scene.Node
import org.slf4j.LoggerFactory
import java.util.*

class DragLayoutLeaf(
    component: Node? = null,
    var layoutPreference: LeafLayoutPreference = LeafLayoutPreference(),
    var node: DragLayoutNode? = null,
    id: String = "leaf-" + UUID.randomUUID().toString(),
) {

    private var _component: Node? = component
    private var _id: String = id

    var component: Node?
        get() = _component
        set(value) { _component = value; adjustComponentId() }

    var id: String
        get() = _id
        set(value) { _id = value; adjustComponentId() }

    var parent: DragLayoutNode? = null
    var boundCache: Rectangle2D? = null

    init {
        if ((isComponent && isNode) || (!isComponent && !isNode))
            throw IllegalArgumentException("The leaf must contain exactly one type")
        adjustParent()
        adjustComponentId()
    }

    private fun adjustParent() {
        if (isNode)
            this.node!!.parent = this
    }

    private fun adjustComponentId() {
        if (isComponent)
            component!!.id = id
    }

    private fun findOrientedParent(dir: Pair<Orientation, Int>): Pair<DragLayoutNode, Int> {
        if (dir.first == parent!!.orientation) {
            // Inline with parent
            val index = parent!!.indexOf(this)
            return parent!! to index + dir.second
        } else {
            // Wrap self in new node with the correct orientation
            val newParent = DragLayoutNode(
                parent = this,
                orientation = dir.first,
                id = ""
            )
            val newThis = DragLayoutLeaf(node = newParent)
            this.swapOnto(newThis)
            newParent.children.add(newThis)
            return newParent to 0 + dir.second
        }
    }

    fun insertAtSide(side: Side, node: DragLayoutLeaf) {
        val parent = findOrientedParent(
            when (side) {
                Side.TOP -> Orientation.VERTICAL to 0
                Side.BOTTOM -> Orientation.VERTICAL to 1
                Side.LEFT -> Orientation.HORIZONTAL to 0
                Side.RIGHT -> Orientation.HORIZONTAL to 1
            }
        )

        parent.first.insertNodes(parent.second, listOf(node))
    }

    /**
     * Unwraps the first child of the contained node.
     *
     * @throws IllegalStateException if not this.isNode
     */
    fun unwrap() {
        if (!this.isNode) throw IllegalStateException("This leaf does not contain a node")
        val c = this.node!!.children[0]
        this.node!!.children.clear()
        c.swapOnto(this)
    }

    fun swapOnto(other: DragLayoutLeaf) {
        logger.debug("#swapOnto this={} {} other={} {}", this.id, this.component?.id, other.id, other.component?.id)

        val tempNode = other.node
        val tempComp = other.component
        val tempId = other.id
        val tempParent = other.parent
        val tempPreference = other.layoutPreference

        other.component = null
        other.node = node
        other.component = component
        other.id = id
        other.parent = tempParent
        other.layoutPreference = layoutPreference

        component = null // prevent bogus id's
        id = tempId
        node = tempNode
        component = tempComp
        parent = tempParent
        layoutPreference = tempPreference
        this.adjustParent()
        other.adjustParent()

        logger.debug("  swapped this={} {} other={} {}", this.id, this.component?.id, other.id, other.component?.id)
    }

    /**
     * @return Bounding boxes of side sections
     */
    fun getSideSections(bounds: Rectangle2D? = null): SideSections {
        val bb = bounds ?: (boundCache ?: throw IllegalArgumentException("No bounds"))
        val marginX = SIDE_CUE_SIZE.coerceAtMost(bb.width / 2.2)
        val marginY = SIDE_CUE_SIZE.coerceAtMost(bb.height / 2.2)
        return SideSections(
            top = Rectangle2D(bb.minX + marginX, bb.minY, bb.width - marginX * 2, marginY),
            bottom = Rectangle2D(bb.minX + marginX, bb.height - marginY + bb.minY, bb.width - marginX * 2, marginY),
            left = Rectangle2D(bb.minX, bb.minY + marginY, marginX, bb.height - 2 * marginY),
            right = Rectangle2D(bb.width - marginX + bb.minX, bb.minY + marginY, marginX, bb.height - 2 * marginY)
        )
    }

    /**
     * Stores the bounding boxes of side sections, where the user can drop additional leafs
     */
    data class SideSections(
        val top: Rectangle2D,
        val bottom: Rectangle2D,
        val left: Rectangle2D,
        val right: Rectangle2D
    ) {
        fun intersect(p: Point2D): Pair<Rectangle2D, Side>? {
            if (top.contains(p)) return top to Side.TOP
            if (bottom.contains(p)) return bottom to Side.BOTTOM
            if (left.contains(p)) return left to Side.LEFT
            if (right.contains(p)) return right to Side.RIGHT
            return null
        }
    }

    val isComponent: Boolean
        get() = component != null

    val isNode: Boolean
        get() = node != null

    companion object {
        private val logger = LoggerFactory.getLogger("DragLayoutLeaf")
    }
}