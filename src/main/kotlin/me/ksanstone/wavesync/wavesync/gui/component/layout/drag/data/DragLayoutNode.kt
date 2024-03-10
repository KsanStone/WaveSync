package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragDivider
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.fire
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.math.abs

const val DIVIDER_SIZE = 5.0
const val SIDE_CUE_SIZE = 60.0

data class DragLayoutNode(
    var id: String,
    var orientation: Orientation = Orientation.HORIZONTAL,
    val children: ObservableList<DragLayoutLeaf> = FXCollections.observableArrayList(),
    var dividerLocations: MutableList<Double> = mutableListOf(),
    var dividers: MutableList<DragDivider> = mutableListOf(),
    var parent: DragLayoutLeaf? = null
) {

    private val layoutChangeListeners = mutableListOf<DragLayout.LayoutChangeListener>()

    var boundCache: Rectangle2D? = null

    init {
        children.addListener(ListChangeListener { change ->
            change.next()
            if (change.wasRemoved()) {
                change.removed.forEach { it.parent = null }
            }
            if (change.wasAdded()) {
                change.addedSubList.forEach { it.parent = this }
            }
            fireChange()
        })
        children.forEach { it.parent = this }
    }

    fun addLayoutChangeListener(listener: DragLayout.LayoutChangeListener) {
        layoutChangeListeners.add(listener)
    }

    private fun fireChange() {
        layoutChangeListeners.fire(this)
        parent?.parent?.fireChange()
    }

    /**
     * Creates [DragDivider] instances for this node, and its children.
     * Old instances are deleted
     */
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

        children.forEach { it.parent = this }
    }

    /**
     * Relocates the given divider. Its position will be clamped
     */
    fun relocateDivider(id: Int, newPos: Double) {
        val newDividerValue = when (orientation) {
            Orientation.HORIZONTAL -> newPos / boundCache!!.width
            Orientation.VERTICAL -> newPos / boundCache!!.height
        }
        val dividerWidth = when (orientation) {
            Orientation.HORIZONTAL -> DIVIDER_SIZE / boundCache!!.width
            Orientation.VERTICAL -> DIVIDER_SIZE / boundCache!!.height
        }
        val minSizePx = 40 // TODO, make components be able to declare this on their own
        val minSizePadding = when (orientation) {
            Orientation.HORIZONTAL -> minSizePx / boundCache!!.width
            Orientation.VERTICAL -> minSizePx / boundCache!!.height
        }

        val dividerPrev = dividerLocations.getOrElse(id - 1) { _ -> 0.0 - dividerWidth } + dividerWidth + minSizePadding
        val dividerNext = dividerLocations.getOrElse(id + 1) { _ -> 1.0 + dividerWidth } - dividerWidth - minSizePadding

        dividerLocations[id] = newDividerValue.coerceIn(dividerPrev, dividerNext)
        fireChange()
    }

    /**
     * Simplifies the tree structure, conjoining any aligned and orphaned nodes
     */
    fun simplify(noRecurse: Boolean = false) {
        if (!noRecurse)
            for (child in children) {
                if (child.isNode)
                    child.node!!.simplify()
            }

        val toRemove = mutableListOf<Int>()
        for (i in children.indices) {
            val child = children[i]
            if (child.isNode && child.node!!.children.size == 1) { // Remove useless container
                child.unwrap()
            } else if (child.isNode && child.node!!.isEmpty()) { // Remove useless container
                toRemove.add(0, i)
            } else if (child.isNode && child.node!!.orientation == orientation) { // Unwrap useless container
                unwrapNode(i, child.node!!.children, child.node!!.dividerLocations)
            }
        }

        for (i in toRemove.reversed()) {
            children.removeAt(i)
            var j = i
            if (i >= dividerLocations.size) j--
            dividerLocations.removeAt(j)
            dividers.removeAt(j)
        }

        if (children.size == 1 && children[0].isNode) { // We only contain one node, unwrap
            val node = children[0].node!!
            this.children.setAll(node.children)
            this.dividers = node.dividers
            this.dividerLocations = node.dividerLocations
            this.orientation = node.orientation
            this.id = node.id
        }

        validateDividers()
    }

    /**
     * Fixes divider locations
     * If the dividers mismatch the nodes, a new set is created
     */
    private fun validateDividers() {
        if (children.size == 0) return this.dividerLocations.clear()
        if (this.dividerLocations.size + 1 != children.size || !rangeValid(this.dividerLocations))
          this.dividerLocations = MutableList(children.size - 1) { (it + 1) / children.size.toDouble() }
    }

    private fun rangeValid(numbers: List<Double>, expectedLength: Int = 0): Boolean {
        if (expectedLength == 0 && numbers.isEmpty()) return true
        for (i in 0 until numbers.size - 1) {
            if (numbers[i] >= numbers[i + 1] || numbers[i] <= 0 || numbers[i] >= 1) {
                return false
            }
        }
        return numbers.first() > 0 && numbers.first() < 1 && numbers.last() > 0 && numbers.last() < 1
    }

    private fun unwrapNode(pos: Int, children: MutableList<DragLayoutLeaf>, dividers: List<Double>) {
        this.children.removeAt(pos)
        this.children.addAll(pos, children)

        val divPrev = dividers.getOrElse(pos - 1) { _ -> 0.0 }
        val divScale = dividers.getOrElse(pos) { _ -> 1.0 } - divPrev
        for (i in dividers.indices) {
            this.dividerLocations.add(pos + i, (dividers[i] / divScale) + divPrev)
        }
    }

    fun insertNodes(pos: Int, nodes: List<DragLayoutLeaf>) {
        val insertedRangeSize = nodes.size.toDouble() / (children.size + nodes.size)

        var rangeStart = dividerLocations.getOrElse(pos - 1) { _ -> 0.0 } - insertedRangeSize / 2
        var rangeEnd = dividerLocations.getOrElse(pos - 1) { _ -> 0.0 } + insertedRangeSize / 2
        var offset = 0
        var rangeMid = (rangeEnd + rangeStart) / 2

        if (pos == 0) {
            rangeStart = 0.0
            rangeEnd = insertedRangeSize
            rangeMid = (rangeEnd + rangeStart) / 2
            if(children.size == 0) offset = -1
        } else if (pos == children.size) {
            rangeStart = 1.0 - insertedRangeSize
            rangeEnd = 1.0
            rangeMid = 1.0
            offset = -1
            dividerLocations.add(1.0)
        }

        spaceOutScalars(rangeMid, rangeStart, rangeEnd, pos)
        for (i in nodes.indices.first .. nodes.indices.last + offset) {
            dividerLocations.add(pos + i, insertedRangeSize * ( i + 1 ) + rangeStart)
        }

        children.addAll(pos, nodes)
    }

    private fun removeChildAt(index: Int): DragLayoutLeaf {
        val child = children.removeAt(index)
        var j = index
        if (index >= dividerLocations.size) j--
        if (!dividerLocations.indices.contains(j)) return child

        val rangeStart = dividerLocations.getOrNull(j - 1) ?: 0.0
        val rangeEnd = dividerLocations.getOrNull(j) ?: 1.0
        val rangeMid = (rangeStart + rangeEnd) / 2

        dividerLocations.removeAt(j)
        dividers.removeAt(j)

        if (dividerLocations.size == 0) return child
        spaceOutScalars(rangeMid, rangeStart, rangeEnd, j, true)

        return child
    }

    private fun spaceOutScalars(rangeMid: Double, rangeStart: Double, rangeEnd: Double, index: Int, inverse: Boolean = false) {
        var divBeforeScalar = rangeStart / rangeMid
        var divAfterScalar = rangeMid / rangeEnd

        if (inverse) {
            divBeforeScalar = 1 / divBeforeScalar
            divAfterScalar = 1 / divAfterScalar
        }

        for (i in 0 until index) { // scale before dividers
            dividerLocations[i] *= divBeforeScalar
        }
        for (i in index until dividerLocations.size) { // scale after dividers
            dividerLocations[i] = 1 - ((1 - dividerLocations[i]) * divAfterScalar)
        }
    }

    /**
     * Calculate the bounds of each child within the node
     *
     * @return a list containing the bounds of each node in order
     */
    fun getChildrenBounds(box: Rectangle2D): List<Rectangle2D?> {
        validateArrayLengths()

        val rects = ArrayList<Rectangle2D?>(children.size)
        var s = 0.0

        for (i in dividerLocations.indices) {
            rects.add(
                when (orientation) {
                    Orientation.HORIZONTAL -> safeRect(s * box.width + box.minX, box.minY, (dividerLocations[i] - s) * box.width, box.height)
                    Orientation.VERTICAL -> safeRect(box.minX, s * box.height + box.minY, box.width, (dividerLocations[i] - s) * box.height)
                }
            )
            s = dividerLocations[i]
        }

        rects.add(
            when (orientation) {
                Orientation.HORIZONTAL -> safeRect(s * box.width + box.minX, box.minY, (1.0 - s) * box.width, box.height)
                Orientation.VERTICAL -> safeRect(box.minX, s * box.height + box.minY, box.width, (1.0 - s) * box.height)
            }
        )

        for (i in dividerLocations.indices) {
            when (orientation) {
                Orientation.HORIZONTAL -> {
                    rects[i] = rects[i]?.let { safeRect(it.minX, it.minY, it.width - DIVIDER_SIZE / 2, it.height) }
                    rects[i + 1] = rects[i + 1]?.let { safeRect(it.minX + DIVIDER_SIZE / 2, it.minY, it.width, it.height) }
                }
                Orientation.VERTICAL -> {
                    rects[i] = rects[i]?.let { safeRect(it.minX, it.minY, it.width, it.height - DIVIDER_SIZE / 2) }
                    rects[i + 1] = rects[i + 1]?.let { safeRect(it.minX, it.minY + DIVIDER_SIZE / 2, it.width, it.height) }
                }
            }
        }

        return rects
    }

    /**
     * @return the bounds of all the dividers contained within this node
     */
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

    private fun safeRect(x: Double, y: Double, w: Double, h: Double): Rectangle2D? {
        if (w <= 0 || h <= 0) return null
        return Rectangle2D(x, y, w, h)
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
                Orientation.HORIZONTAL -> if (abs(point.x - i) <= dividerMargin.x) return null
                Orientation.VERTICAL -> if (abs(point.y - i) <= dividerMargin.y) return null
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
     * @return true if iteration has been stopped preemptively by calling [ComponentCallbackResult.stop]
     */
    fun iterateComponents(callback: Consumer<ComponentCallbackResult>): Boolean {
        this.children.forEach {
            if (it.isComponent) {
                val res = ComponentCallbackResult(it.component!!, it.id)
                callback.accept(res)
                if (res.stop) return true
            } else if (it.isNode) {
                if(it.node!!.iterateComponents(callback)) return true
            }
        }; return false
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

    fun indexOf(leaf: DragLayoutLeaf): Int {
        return children.indexOf(leaf)
    }

    fun isEmpty(): Boolean {
        return children.size == 0
    }

    /**
     * Finds a leaf matching the given predicate if that leaf contains a javafx [Node],
     * and removes it from children
     * @return [DragLayoutLeaf] or null if no such leaf exists
     */
    fun cutComponent(predicate: Predicate<DragLayoutLeaf>): DragLayoutLeaf? {
        for (i in children.indices) {
            val it = children[i]
            if (it.isComponent) {
                if (predicate.test(it)) {
                    return removeChildAt(i)
                }
            } else if (it.isNode) {
                val childSearchResult = it.node!!.cutComponent(predicate)
                if (childSearchResult != null) return childSearchResult
            }
        }
        return null
    }

    /**
     * Finds a leaf with the given id if that leaf contains a javafx [Node],
     * and removes it from children
     * @return [DragLayoutLeaf] or null if no such leaf exists
     */
    fun cutComponentLeaf(id: String): DragLayoutLeaf? {
        return cutComponent {it.id == id}
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

    /**
     * Checks whether an instance of the given class is present
     * in this node's tree
     */
    fun queryComponentOfClassExists(clazz: Class<*>): Boolean {
        var found = false
        this.iterateComponents {
            if (it.node.javaClass == clazz) {
                it.stop()
                found = true
            }
        }
        return found
    }

    /**
     * Removes a component of the given class
     * @return true if any component was removed
     */
    fun removeComponentOfClass(clazz: Class<*>): Boolean {
        val cutComp = cutComponent { t -> t.component!!::class.java.name == clazz.name }
        if (cutComp != null) {
            fireChange()
            return true
        }
        return false
    }

    private fun validateArrayLengths() {
        if (dividerLocations.size != children.size - 1 && (dividerLocations.size + children.size) != 0)
            throw IllegalArgumentException("Divider [${dividerLocations.size}] and children [${children.size}] lists are mismatched")
    }
}

data class ComponentCallbackResult(
    val node: Node,
    val nodeId: String
) {
    internal var stop: Boolean = false

    /**
     * Preemptively stop iteration
     */
    fun stop() {
        this.stop = true
    }
}

data class NodeCallbackResult(
    val node: DragLayoutNode,
)

data class DividerCallbackResult(
    val divider: DragDivider,
    val loc: Double
)