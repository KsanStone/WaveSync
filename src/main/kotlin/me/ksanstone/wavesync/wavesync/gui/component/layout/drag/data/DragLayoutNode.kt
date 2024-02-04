package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data

import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragDivider
import java.util.function.Consumer
import kotlin.math.abs

const val DIVIDER_SIZE = 5.0
const val SIDE_CUE_SIZE = 60.0

data class DragLayoutNode(
    var id: String,
    var orientation: Orientation = Orientation.HORIZONTAL,
    var children: MutableList<DragLayoutLeaf> = mutableListOf(),
    var dividerLocations: MutableList<Double> = mutableListOf(),
    val dividers: MutableList<DragDivider> = mutableListOf()
) {

    var boundCache: Rectangle2D? = null

    fun createDividers() {
        this.dividers.clear()
        this.dividers.addAll(this.dividerLocations.indices.map {
            DragDivider(
                orientation.run { if (orientation == Orientation.VERTICAL) Orientation.HORIZONTAL else Orientation.VERTICAL },
                this,
                it
            )
        })

        this.iterateNodes {
            it.node.createDividers()
        }
    }

    fun relocateDivider(id: Int, newPos: Double) {
        val newDividerValue = when(orientation) {
            Orientation.HORIZONTAL -> newPos / boundCache!!.width
            Orientation.VERTICAL -> newPos / boundCache!!.height
        }
        val dividerWidth = when(orientation) {
            Orientation.HORIZONTAL -> DIVIDER_SIZE / boundCache!!.width
            Orientation.VERTICAL -> DIVIDER_SIZE / boundCache!!.height
        }
        val minSizePx = 40 // TODO, make components be able to declare this on their own
        val minSizePadding = when(orientation) {
            Orientation.HORIZONTAL -> minSizePx / boundCache!!.width
            Orientation.VERTICAL -> minSizePx / boundCache!!.height
        }

        val dividerPrev = dividerLocations.getOrElse(id - 1) { _ -> 0.0 - dividerWidth } + dividerWidth + minSizePadding
        val dividerNext = dividerLocations.getOrElse(id + 1) { _ -> 1.0 + dividerWidth } - dividerWidth - minSizePadding

        dividerLocations[id] = newDividerValue.coerceIn(dividerPrev, dividerNext)
    }

    /**
     * Calculate the bounds of each child within the node
     *
     * @return a list containing the bounds of each node in order
     */
    fun getChildrenBounds(box: Rectangle2D): List<Rectangle2D> {
        validateArrayLengths()

        val rects = ArrayList<Rectangle2D>(children.size)
        var s = 0.0

        for (i in dividerLocations.indices) {
            rects.add(
                when (orientation) {
                    Orientation.HORIZONTAL -> Rectangle2D(s * box.width + box.minX, box.minY, (dividerLocations[i] - s) * box.width, box.height)
                    Orientation.VERTICAL -> Rectangle2D(box.minX, s * box.height + box.minY, box.width, (dividerLocations[i] - s) * box.height)
                }
            )
            s = dividerLocations[i]
        }

        rects.add(
            when (orientation) {
                Orientation.HORIZONTAL -> Rectangle2D(s * box.width + box.minX, box.minY, (1.0 - s) * box.width, box.height)
                Orientation.VERTICAL -> Rectangle2D(box.minX, s * box.height + box.minY, box.width, (1.0 - s) * box.height)
            }
        )

        for (i in dividerLocations.indices) {
            when (orientation) {
                Orientation.HORIZONTAL -> {
                    rects[i] = Rectangle2D(rects[i].minX, rects[i].minY, rects[i].width - DIVIDER_SIZE / 2, rects[i].height)
                    rects[i + 1] = Rectangle2D(rects[i + 1].minX + DIVIDER_SIZE / 2, rects[i + 1].minY, rects[i + 1].width, rects[i + 1].height)
                }
                Orientation.VERTICAL -> {
                    rects[i] = Rectangle2D(rects[i].minX, rects[i].minY, rects[i].width, rects[i].height - DIVIDER_SIZE / 2)
                    rects[i + 1] = Rectangle2D(rects[i + 1].minX, rects[i + 1].minY + DIVIDER_SIZE / 2, rects[i + 1].width, rects[ + 1].height)
                }
            }
        }

        return rects
    }

    fun getDividerBounds(box: Rectangle2D): List<Rectangle2D> {
        validateArrayLengths()

        val rects = ArrayList<Rectangle2D>(dividers.size)
        val offset = DIVIDER_SIZE / 2

        for (i in dividerLocations.indices) {
            val s = dividerLocations[i]
            rects.add(
                when (orientation) {
                    Orientation.HORIZONTAL -> Rectangle2D(s * box.width + box.minX - offset, box.minY, DIVIDER_SIZE, box.height)
                    Orientation.VERTICAL -> Rectangle2D(box.minX, s * box.height + box.minY - offset, box.width, DIVIDER_SIZE)
                }
            )
        }

        return rects
    }

    /**
     * Intersects the point with this node's children.
     * If the child is itself a [DragLayoutNode] the a recursive call to its .intersect method is made,
     * until a [DragLayoutLeaf].isComponent node is found.
     *
     * @return DragLayoutLeaf containing a javafx Node
     */
    fun intersect(point: Point2D, dividerMargin: Point2D): DragLayoutLeaf? {
        validateArrayLengths()

        if (children.size == 0) return null

        // Check for divider intersection
        for (i in dividerLocations) {
            when (orientation) {
                Orientation.HORIZONTAL -> if(abs(point.x - i) <= dividerMargin.x) return null
                Orientation.VERTICAL -> if(abs(point.y - i) <= dividerMargin.y) return null
            }
        }

        // Note: Children will always occupy the entire width/height (depending on the orientation) of this component
        // As such a simple divider check will suffice
        for (i in dividerLocations.indices) {
            val target = when (orientation) {
                Orientation.HORIZONTAL -> point.x
                Orientation.VERTICAL -> point.y
            }

            if (target < dividerLocations[i])
                return handleChildIntersect(i, point, dividerMargin)
        }
        // Last child
        return handleChildIntersect(children.size - 1, point, dividerMargin)
    }

    /**
     * If the intersected node contains another node,
     * recursively call its intersect method with the
     * appropriately scaled intersection point.
     */
    private fun handleChildIntersect(i: Int, point: Point2D, dividerMargin: Point2D): DragLayoutLeaf? {
        return if (children[i].isComponent) children[i]
        else if (children[i].isNode) children[i].node!!.intersect(
            scalePointToChildBounds(
                point, rangeStart = dividerLocations.getOrElse(i - 1) { _ -> 0.0 },
                rangeEnd = if (i < dividerLocations.size) dividerLocations[i] else 1.0, orientation
            ), dividerMargin
        ) else null
    }

    private fun scalePointToChildBounds(
        point: Point2D,
        rangeStart: Double,
        rangeEnd: Double,
        orientation: Orientation
    ): Point2D {
        return when (orientation) {
            Orientation.HORIZONTAL -> Point2D((point.x - rangeStart) / (rangeEnd - rangeStart), point.y)
            Orientation.VERTICAL -> Point2D(point.x, (point.y - rangeStart) / (rangeEnd - rangeStart))
        }
    }

    /**
     * Recursively iterates over every javafx [Node] in this layout node
     *
     * @param callback Callback to be called on each node
     */
    fun iterateComponents(callback: Consumer<ComponentCallbackResult>) {
        this.children.forEach {
            if (it.isComponent) callback.accept(ComponentCallbackResult(it.component!!, it.id))
            else if (it.isNode) it.node!!.iterateComponents(callback)
        }
    }

    /**
     * Recursively iterates over every [DragLayoutNode] in this layout node
     *
     * @param callback Callback to be called on each node
     */
    fun iterateNodes(callback: Consumer<NodeCallbackResult>) {
        this.children.forEach {
            if (it.isNode) {
                callback.accept(NodeCallbackResult(it.node!!))
                it.node!!.iterateNodes(callback)
            }
        }
    }

    /**
     * Recursively iterates over every [DragDivider] component
     *
     * @param callback Callback to be called on each divider
     */
    fun iterateDividers(callback: Consumer<DividerCallbackResult>) {
        iterDiv(callback)
        this.iterateNodes {
            it.node.iterDiv(callback)
        }
    }

    private fun iterDiv(callback: Consumer<DividerCallbackResult>) {
        this.dividers.indices.forEach {
            callback.accept(DividerCallbackResult(dividers[it], dividerLocations[it]))
        }
    }

    /**
     * Finds a leaf with the given id if that leaf contains a javafx [Node]
     * @return [DragLayoutLeaf] or null if no such leaf exists
     */
    fun findComponentLeaf(id: String): DragLayoutLeaf? {
        this.children.forEach {
            if (it.isComponent) {
                if (it.id == id) return it
            } else if (it.isNode) {
                val childSearchResult = it.node!!.findComponentLeaf(id)
                if (childSearchResult != null) return childSearchResult
            }
        }
        return null
    }

    private fun validateArrayLengths() {
        if (dividerLocations.size != children.size - 1) throw IllegalArgumentException("Divider and children lists are mismatched")
    }
}

data class ComponentCallbackResult(
    val node: Node,
    val nodeId: String
)

data class NodeCallbackResult(
    val node: DragLayoutNode,
)

data class DividerCallbackResult(
    val divider: DragDivider,
    val loc: Double
)