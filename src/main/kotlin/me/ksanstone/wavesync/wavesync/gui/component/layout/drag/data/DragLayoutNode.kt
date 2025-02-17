package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Dimension2D
import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragDivider
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.event.DragLayoutEvent
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.event.LayoutChangeEvent
import me.ksanstone.wavesync.wavesync.utility.EventEmitter
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrDefault
import kotlin.math.abs
import kotlin.math.max

const val DIVIDER_SIZE = 5.0
const val SIDE_CUE_SIZE = 60.0

data class DragLayoutNode(
    var id: String,
    var orientation: Orientation = Orientation.HORIZONTAL,
    val children: ObservableList<DragLayoutLeaf> = FXCollections.observableArrayList(),
    var dividerLocations: ObservableList<Double> = FXCollections.observableArrayList(),
    var dividers: MutableList<DragDivider> = mutableListOf(),
    var parent: DragLayoutLeaf? = null,
    var layout: WeakReference<DragLayout>? = null,
) {

    val eventEmitter = EventEmitter<DragLayoutEvent>()

    private var _boundCache: Rectangle2D? = null
    var boundCache: Rectangle2D?
        set(value) {
            _boundCache = value
            evenDividerCache = null
            aspectRatioAwareDividerCache = null
        }
        get() = _boundCache


    private var evenDividerCache: List<Double>? = null
    private var aspectRatioAwareDividerCache: Optional<List<Double>>? = null

    init {
        children.addListener(ListChangeListener { change ->
            change.next()
            if (change.wasRemoved()) {
                change.removed.forEach { it.parent = null }
            }
            if (change.wasAdded()) {
                change.addedSubList.forEach {
                    it.parent = this
                    it.node?.eventEmitter?.bubbleTo(eventEmitter)
                }
            }
            evenDividerCache = null
            aspectRatioAwareDividerCache = null
            fireChange()
        })
        children.forEach { it.parent = this }
        dividerLocations.addListener(ListChangeListener { change ->
            change.next()

            if (dividers.size < dividerLocations.size) {
                for (i in dividers.size until dividerLocations.size) {
                    dividers.add(
                        DragDivider(
                            orientation.run { if (orientation == Orientation.VERTICAL) Orientation.HORIZONTAL else Orientation.VERTICAL },
                            this,
                            i
                        )
                    )
                }
            } else if (dividers.size > dividerLocations.size) {
                for (i in dividers.size - 1 downTo dividerLocations.size) {
                    dividers.removeAt(i)
                }
            }
        })
        this.dividers.addAll(this.dividerLocations.indices.map {
            DragDivider(
                orientation.run { if (orientation == Orientation.VERTICAL) Orientation.HORIZONTAL else Orientation.VERTICAL },
                this,
                it
            )
        })
    }

    private fun fireChange(event: LayoutChangeEvent = LayoutChangeEvent(this)) {
        eventEmitter.publish(event)
    }

    /**
     * Relocates the given divider. Its position will be clamped
     */
    fun relocateDivider(id: Int, newPos: Double, snap: Boolean) {
        if (boundCache == null) return

        val scalar = when (orientation) {
            Orientation.HORIZONTAL -> boundCache!!.width
            Orientation.VERTICAL -> boundCache!!.height
        }

        var newDividerValue = newPos / scalar
        if (snap) {
            val points = getSnapPoints()
            val epsilon = 20 / scalar

            for (point in points) {
                if (abs(point - newDividerValue) < epsilon) {
                    newDividerValue = point
                    break
                }
            }
        }


        val dividerWidth = DIVIDER_SIZE / scalar

        val compPrev = children[id]
        val minSizePaddingPrev = compPrev.layoutPreference.minSize / scalar
        val compNext = children[id + 1]
        val minSizePaddingNext = compNext.layoutPreference.minSize / scalar

        val dividerPrev =
            dividerLocations.getOrElse(id - 1) { _ -> 0.0 - dividerWidth } + dividerWidth + minSizePaddingPrev
        val dividerNext =
            dividerLocations.getOrElse(id + 1) { _ -> 1.0 + dividerWidth } - dividerWidth - minSizePaddingNext

        dividerLocations[id] = newDividerValue.coerceIn(dividerPrev, dividerNext)
        fireChange()
    }

    /**
     * Simplifies the tree structure, conjoining any aligned and orphaned nodes
     */
    fun simplify(noRecurse: Boolean = false) {
        if (!noRecurse)
        // Simplify the children first
        // to ensure that when we remove empty nodes, we only remove actually empty nodes
            for (child in children) {
                if (child.isNode)
                    child.node!!.simplify()
            }

        val toRemove = mutableListOf<Int>() // Prevent concurrent modifications
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

        // Apply removes
        for (i in toRemove.reversed()) {
            children.removeAt(i)
            var j = i
            if (i >= dividerLocations.size) j--
            if (dividerLocations.indices.contains(j))
                dividerLocations.removeAt(j)
            if (dividers.indices.contains(j))
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

    fun afterTransition() {
        simplify()
    }

    /**
     * Fixes divider locations
     * If the dividers mismatch the nodes, a new set is created
     */
    private fun validateDividers() {
        if (children.size == 0) return this.dividerLocations.clear()
        if (this.dividerLocations.size + 1 != children.size || !rangeValid(this.dividerLocations))
            this.dividerLocations.setAll(MutableList(children.size - 1) { (it + 1) / children.size.toDouble() })
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
            if (children.size == 0) offset = -1
        } else if (pos == children.size) {
            rangeStart = 1.0 - insertedRangeSize
            rangeEnd = 1.0
            rangeMid = 1.0
            offset = -1
            dividerLocations.add(1.0)
        }

        spaceOutScalars(rangeMid, rangeStart, rangeEnd, pos)
        for (i in nodes.indices.first..nodes.indices.last + offset) {
            dividerLocations.add(pos + i, insertedRangeSize * (i + 1) + rangeStart)
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

        if (dividerLocations.size == 0) return child
        spaceOutScalars(rangeMid, rangeStart, rangeEnd, j, true)

        return child
    }

    private fun spaceOutScalars(
        rangeMid: Double,
        rangeStart: Double,
        rangeEnd: Double,
        index: Int,
        inverse: Boolean = false,
    ) {
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
                    Orientation.HORIZONTAL -> safeRect(
                        s * box.width + box.minX,
                        box.minY,
                        (dividerLocations[i] - s) * box.width,
                        box.height
                    )

                    Orientation.VERTICAL -> safeRect(
                        box.minX,
                        s * box.height + box.minY,
                        box.width,
                        (dividerLocations[i] - s) * box.height
                    )
                }
            )
            s = dividerLocations[i]
        }

        rects.add(
            when (orientation) {
                Orientation.HORIZONTAL -> safeRect(
                    s * box.width + box.minX,
                    box.minY,
                    (1.0 - s) * box.width,
                    box.height
                )

                Orientation.VERTICAL -> safeRect(box.minX, s * box.height + box.minY, box.width, (1.0 - s) * box.height)
            }
        )

        for (i in dividerLocations.indices) {
            when (orientation) {
                Orientation.HORIZONTAL -> {
                    rects[i] = rects[i]?.let { safeRect(it.minX, it.minY, it.width - DIVIDER_SIZE / 2, it.height) }
                    rects[i + 1] =
                        rects[i + 1]?.let { safeRect(it.minX + DIVIDER_SIZE / 2, it.minY, it.width, it.height) }
                }

                Orientation.VERTICAL -> {
                    rects[i] = rects[i]?.let { safeRect(it.minX, it.minY, it.width, it.height - DIVIDER_SIZE / 2) }
                    rects[i + 1] =
                        rects[i + 1]?.let { safeRect(it.minX, it.minY + DIVIDER_SIZE / 2, it.width, it.height) }
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
                    Orientation.HORIZONTAL -> Rectangle2D(
                        s * box.width + box.minX - offset,
                        box.minY,
                        DIVIDER_SIZE,
                        box.height
                    )

                    Orientation.VERTICAL -> Rectangle2D(
                        box.minX,
                        s * box.height + box.minY - offset,
                        box.width,
                        DIVIDER_SIZE
                    )
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
        orientation: Orientation,
    ): Point2D {
        return when (orientation) {
            Orientation.HORIZONTAL -> Point2D((point.x - rangeStart) / (rangeEnd - rangeStart), point.y)
            Orientation.VERTICAL -> Point2D(point.x, (point.y - rangeStart) / (rangeEnd - rangeStart))
        }
    }

    /**
     * Get the node id's of all components.
     * Used to check if the component with the given id is already present
     * in the component tree to prevent re-adding it.
     */
    fun collectComponentIds(): List<String> {
        val ids = mutableListOf<String>()
        forEachComponent {
            ids.add(it.nodeId)
            if (it.nodeId != it.node.id) logger.debug("Node-Component ID-Mismatch: leaf {} -> {}", it.nodeId, it.node.id)
        }
        return ids
    }

    /**
     * Recursively iterates over every javafx [Node] in this layout node
     *
     * @param callback Callback to be called on each node
     * @return true if iteration has been stopped preemptively by calling [ComponentCallbackResult.stop]
     */
    fun forEachComponent(callback: Consumer<ComponentCallbackResult>): Boolean {
        this.children.forEach {
            if (it.isComponent) {
                val res = ComponentCallbackResult(it.component!!, it.id)
                callback.accept(res)
                if (res.stop) return true
            } else if (it.isNode) {
                if (it.node!!.forEachComponent(callback)) return true
            }
        }; return false
    }

    /**
     * Recursively iterates over every [DragLayoutNode] in this layout node
     *
     * @param callback Callback to be called on each node
     */
    private fun forEachNode(callback: Consumer<NodeCallbackResult>) {
        iterateNodesInternal(callback, 0)
    }

    private fun iterateNodesInternal(callback: Consumer<NodeCallbackResult>, depth: Int = 0) {
        this.children.forEach {
            if (it.isNode) {
                callback.accept(NodeCallbackResult(it.node!!, depth))
                it.node!!.iterateNodesInternal(callback, depth + 1)
            }
        }
    }

    /**
     * Recursively iterates over every [DragDivider] component
     *
     * @param callback Callback to be called on each divider
     */
    private fun forEachDivider(callback: Consumer<DividerCallbackResult>) {
        iterateDividersInternal(callback, 0)
        this.forEachNode {
            // Directly nested nodes will have a depth of 0, however their dividers are at a depth of 1 already
            it.node.iterateDividersInternal(callback, it.depth + 1)
        }
    }

    /**
     * Recursively iterates over every [DragDivider] component,
     * and collects them to a list.
     */
    fun collectDividers(): MutableList<DividerCallbackResult> {
        val dividers = mutableListOf<DividerCallbackResult>()
        forEachDivider(dividers::add)
        return dividers
    }

    private fun iterateDividersInternal(callback: Consumer<DividerCallbackResult>, depth: Int) {
        this.dividers.indices.forEach {
            callback.accept(DividerCallbackResult(dividers[it], dividerLocations[it], depth))
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
    private fun cutComponent(predicate: Predicate<DragLayoutLeaf>): DragLayoutLeaf? {
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
        return cutComponent { it.id == id }
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
    fun queryComponentExists(id: String): Boolean {
        var found = false
        this.forEachComponent {
            if (it.nodeId == id) {
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
    fun removeComponent(id: String): Boolean {
        val cutComp = cutComponent { t -> t.id == id }
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

    fun toLeaf(): DragLayoutLeaf {
        return DragLayoutLeaf(node = this)
    }

    fun getEffectiveLayout(): WeakReference<DragLayout> {
        if (this.layout != null)
            return layout!!
        return this.parent!!.parent!!.getEffectiveLayout()
    }

    /**
     * Evenly space all the drag bars
     */
    fun justify(recurse: Boolean = false) {
        doJustify()
        if (recurse) this.forEachNode { it.node.doJustify() }
        this.fireChange()
    }

    private fun doJustify() {
        val newDividers = getAspectRatioAwareDividers().getOrDefault(getEvenlySpacedDividers())
        this.dividerLocations.setAll(newDividers)
    }

    private fun getSnapPoints(): Set<Double> {
        val points = mutableSetOf<Double>()
        points.addAll(getEvenlySpacedDividers())
        if (getAspectRatioAwareDividers().isPresent)
            points.addAll(getAspectRatioAwareDividers().get())
        return points
    }

    /**
     * Calculates the divider positions while accounting for the preferred aspect ratio of each component.
     * The positioning isn't always possible, in that case Optional.empty() is returned.
     *
     * The positioning is not possible when:
     * - There is only one child (no dividers).
     * - The boundCache has not been computed yet.
     * - No components have predefined aspect ratios.
     * - The justified components do not fit along with other components in the node.
     *
     * @return optional list of calculated divider positions
     */
    fun getAspectRatioAwareDividers(): Optional<List<Double>> {
        if (aspectRatioAwareDividerCache != null) return aspectRatioAwareDividerCache!!
        if (boundCache == null || children.size < 1) {
            aspectRatioAwareDividerCache = Optional.empty()
            return aspectRatioAwareDividerCache!!
        }

        val fixedSize = when (orientation) {
            Orientation.HORIZONTAL -> boundCache!!.height
            Orientation.VERTICAL -> boundCache!!.width
        }

        val flexibleSize = when (orientation) {
            Orientation.HORIZONTAL -> boundCache!!.width
            Orientation.VERTICAL -> boundCache!!.height
        }

        // Compute pixel widths
        val justifiedWidths = MutableList(children.size) { 0.0 }
        var justified = 0
        for (childIndex in children.indices) {
            val preferredRatio = children[childIndex].layoutPreference.preferredAspectRatio
            if (preferredRatio != null) {
                justified++
                justifiedWidths[childIndex] = fixedSize * preferredRatio
            }
        }

        if (justified == 0) {
            aspectRatioAwareDividerCache = Optional.empty()
            return aspectRatioAwareDividerCache!!
        }
        val nonJustified = justifiedWidths.size - justified
        val pixelJustifiedWidth = justifiedWidths.sum()
        if (pixelJustifiedWidth > flexibleSize - nonJustified /* some padding */) {
            aspectRatioAwareDividerCache = Optional.empty()
            return aspectRatioAwareDividerCache!!
        }

        // Compute indexed width
        for (index in justifiedWidths.indices) {
            if (justifiedWidths[index] != 0.0) {
                justifiedWidths[index] = justifiedWidths[index] / flexibleSize
            }
        }
        val indexedJustifiedWidth = justifiedWidths.sum()
        val indexedNonJustifiedWidth = (1 - indexedJustifiedWidth) / nonJustified

        val newDividers = MutableList(children.size - 1) { 0.0 }
        var x = 0.0
        for (i in 0 until children.size - 1) {
            x += if (justifiedWidths[i] != 0.0) {
                justifiedWidths[i]
            } else {
                indexedNonJustifiedWidth
            }
            newDividers[i] = x
        }

        aspectRatioAwareDividerCache = Optional.of(newDividers)
        return aspectRatioAwareDividerCache!!
    }

    /**
     * Generates dividers that evenly size every component.
     *
     * @see generateDividerPositions
     */
    fun getEvenlySpacedDividers(): List<Double> {
        if (evenDividerCache != null) return evenDividerCache!!
        val positions = generateDividerPositions(this.dividerLocations.size)
        evenDividerCache = positions
        return positions
    }

    /**
     * Calculate the minimum pixel size of the layout where every minimum size constraint is satisfied
     */
    fun calculateMinSize(): Dimension2D {
        var inline = DIVIDER_SIZE * dividers.size
        var axis = 0.0

        for (child in children) {
            val dim = if (child.isNode) child.node!!.calculateMinSize() else Dimension2D(
                child.layoutPreference.minSize,
                child.layoutPreference.minSize
            )

            when (orientation) {
                Orientation.HORIZONTAL -> {
                    inline += dim.width
                    axis = max(axis, dim.height)
                }

                Orientation.VERTICAL -> {
                    inline += dim.height
                    axis = max(axis, dim.width)
                }
            }
        }

        return when (orientation) {
            Orientation.HORIZONTAL -> Dimension2D(inline, axis)
            Orientation.VERTICAL -> Dimension2D(axis, inline)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("DragLayoutNode")

        /**
         * Generate a list of evenly spaced points in the range [0,1],
         * that divide the range into even chunks.
         *
         * - n=1: {0.5}
         * - n=2: {0.333, 0.666}
         * - n=3: {0.25, 0.5, 0.75}
         * ...
         */
        fun generateDividerPositions(size: Int): List<Double> {
            if (size < 1) return mutableListOf()
            val dividers = mutableListOf<Double>()
            val step = 1.0 / (size + 1)
            for (i in 1..size) {
                dividers.add(i * step)
            }
            return dividers
        }
    }
}

data class ComponentCallbackResult(
    val node: Node,
    val nodeId: String,
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
    /**
     * How nested the node is in the component tree.
     */
    val depth: Int
)

data class DividerCallbackResult(
    val divider: DragDivider,
    val loc: Double,
    /**
     * How nested the divider is in the component tree,
     * deeper dividers should render first
     */
    val depth: Int
)