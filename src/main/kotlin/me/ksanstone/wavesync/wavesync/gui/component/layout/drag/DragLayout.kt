package me.ksanstone.wavesync.wavesync.gui.component.layout.drag

import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.Pane
import javafx.scene.transform.Transform
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DIVIDER_SIZE
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutLeaf
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.event.DragLayoutEvent
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.event.LayoutChangeEvent
import me.ksanstone.wavesync.wavesync.service.GlobalLayoutService
import me.ksanstone.wavesync.wavesync.utility.EventEmitter
import java.lang.ref.WeakReference
import java.util.function.Consumer
import java.util.stream.Collectors

class DragLayout : Pane() {

    var layoutRoot: DragLayoutNode = DragLayoutNode("root", layout = WeakReference(this))

    private val dragCueShowing = SimpleBooleanProperty(false)
    private val drawCueRect: Pane = Pane()
    private val layoutLock = Object()
    private val eventEmitter = EventEmitter<DragLayoutEvent>()
    private val gls = WaveSyncBootApplication.applicationContext.getBean(GlobalLayoutService::class.java)
    private val multiScreen = SimpleBooleanProperty(true)

    init {
        setOnDragOver(this::onDragOver)
        setOnDragEntered { }
        setOnDragExited { dragExited() }
        setOnDragDropped(this::dropHandler)

        drawCueRect.visibleProperty().bind(dragCueShowing)
        drawCueRect.styleClass.add("drag-cue")
        styleClass.setAll("drag-layout")
        stylesheets.add("/styles/drag-layout.css")

        layoutRoot.eventEmitter.bubbleTo(eventEmitter)
    }

    fun addLayoutChangeListener(listener: Consumer<LayoutChangeEvent>) {
        eventEmitter.on(LayoutChangeEvent::class.java, listener)
    }

    /**
     * Load up a new root layout
     */
    fun load(node: DragLayoutNode) {
        layoutRoot = node
        layoutRoot.layout = WeakReference(this)
        layoutRoot.eventEmitter.bubbleTo(eventEmitter)
        updateChildren()
    }

    /**
     * Insert a node somewhere into this layout
     *
     * @param node The node to be inserted
     * @param id the (hopefully) unique ID of the node
     */
    fun addComponent(node: Node, id: String) {
        layoutRoot.insertNodes(0, mutableListOf(DragLayoutLeaf(component = node, id = id)))
    }

    private fun onDragOver(e: DragEvent) {
        if (dragOver(Point2D(e.x, e.y), e.dragboard.string))
            e.acceptTransferModes(TransferMode.MOVE)
    }

    fun dragExited() {
        dragCueShowing.value = false
    }

    fun dragOver(p: Point2D, nodeId: String): Boolean {
        dragCueShowing.value = false
        val noteId = decodeNoteId(nodeId) ?: return false
        val intersectedNode =
            layoutRoot.intersect(xyToAbsolute(p), getDividerMargin()) ?: return false
        if (intersectedNode.boundCache == null) return false
        if (noteId == intersectedNode.id) return false


        var queBounds = intersectedNode.boundCache!!
        val side = intersectedNode.getSideSections().intersect(p)
        if (side != null)
            queBounds = side.first

        drawCueRect.resizeRelocate(queBounds)
        dragCueShowing.value = true
        return true
    }

    private fun dropHandler(e: DragEvent) {
        val nodeId = decodeNoteId(e.dragboard.string) ?: return
        dragDropped(layoutRoot, nodeId, Point2D(e.x, e.y))
    }

    fun dragDropped(sourceLayout: DragLayoutNode, nodeId: String, p: Point2D) {
        val x = p.x
        val y = p.y
        try {
            val intersectedNode = layoutRoot.intersect(xyToAbsolute(Point2D(x, y)), getDividerMargin()) ?: return
            if (intersectedNode.boundCache == null) return
            if (nodeId == intersectedNode.id) return

            val side = intersectedNode.getSideSections().intersect(Point2D(x, y))
            if (side != null) {
                splitSide(sourceLayout, side.second, nodeId, intersectedNode.id)
            } else {
                swapNodes(sourceLayout, nodeId, intersectedNode.id)
            }
            eventEmitter.publish(LayoutChangeEvent(layoutRoot))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun justify() {
        layoutRoot.justify(true)
    }

    private fun swapNodes(sourceLayout: DragLayoutNode, targetId: String, destinationId: String) {
        val targetNode = layoutRoot.findComponentLeaf(targetId) ?: return
        val destNode = sourceLayout.findComponentLeaf(destinationId) ?: return
        targetNode.swapOnto(destNode)
        layoutChildren()
    }

    private fun splitSide(sourceLayout: DragLayoutNode, side: Side, source: String, target: String) {
        synchronized(layoutLock) {
            val sourceNode = sourceLayout.cutComponentLeaf(source) ?: return
            val targetNode = layoutRoot.findComponentLeaf(target) ?: return
            targetNode.insertAtSide(side, sourceNode)
            layoutRoot.afterTransition()
            if (sourceLayout != layoutRoot)
                sourceLayout.afterTransition()
            updateChildren()
            layoutChildren()
        }
    }

    fun fullUpdate() {
        this.layoutRoot.simplify()
        this.updateChildren()
        this.layoutChildren()
    }

    /**
     * Ensures all the layout child nodes are added to this component's children list
     */
    private fun updateChildren() {
        this.children.clear()
        layoutRoot.createDividers()
        layoutRoot.forEachComponent {
            it.node.setOnDragDetected { _ ->
                if (multiScreen.get()) {
                    gls.startTransaction(it.nodeId, this)
                } else {
                    val db = it.node.startDragAndDrop(TransferMode.MOVE)
                    val content = ClipboardContent()
                    content.putString(encodeNodeId(it.nodeId))
                    db.setContent(content)
                }
            }
            it.node.setOnMouseDragged { e ->
                if (multiScreen.get()) gls.updateTransaction(Point2D(e.screenX, e.screenY))
            }
            it.node.setOnMouseReleased { e ->
                if (multiScreen.get()) gls.finishTransaction(Point2D(e.screenX, e.screenY))
            }
            this.children.add(it.node)
        }

        // Lay out dividers in depth to prevent overlap
        val dividers = layoutRoot.collectDividers()
        dividers.sortByDescending { it.depth }
        this.children.addAll(dividers.stream().map { it.divider }.collect(Collectors.toList()))

        this.children.add(drawCueRect)
    }

    @Suppress("unused")
    private fun getSnapshotParameters(bb: Bounds): SnapshotParameters {
        val desiredWidth = 170
        val desiredHeight = 170

        var xScale = desiredWidth / bb.width
        var yScale = xScale
        if (yScale * bb.height > desiredHeight) {
            yScale = desiredHeight / bb.height
            xScale = yScale
        }

        val params = SnapshotParameters()
        params.transform = Transform.scale(xScale, yScale)
        return params
    }

    override fun layoutChildren() {
        synchronized(layoutLock) {
            if (!layoutRoot.isEmpty()) {
                layoutNode(layoutRoot, Rectangle2D(0.0, 0.0, width, height))
            }
        }
    }

    private fun layoutNode(node: DragLayoutNode, place: Rectangle2D) {
        val childBounds = node.getChildrenBounds(place)
        node.boundCache = place

        for (i in childBounds.indices) {
            childBounds[i]?.let {
                node.children[i].boundCache = it
                if (node.children[i].isComponent) {
                    node.children[i].component!!.resizeRelocate(it)
                } else if (node.children[i].isNode) {
                    layoutNode(node.children[i].node!!, it)
                }
            } ?: run {
                node.children[i].boundCache = null
            }
        }

        val dividerBounds = node.getDividerBounds(place)
        for (i in dividerBounds.indices) {
            node.dividers[i].resizeRelocate(dividerBounds[i])
        }
    }

    /**
     * Converts a component-based coordinate to an absolute 0 .. 1 scale coordinate
     */
    private fun xyToAbsolute(xy: Point2D): Point2D {
        return Point2D(xy.x / width, xy.y / height)
    }

    private fun getDividerMargin(): Point2D {
        return Point2D(DIVIDER_SIZE / width, DIVIDER_SIZE / height)
    }

    private fun decodeNoteId(encoded: String): String? {
        if (encoded.startsWith("<node-transfer-") && encoded.endsWith(">"))
            return encoded.substring(15, encoded.length - 1)
        return null
    }

    companion object {
        fun encodeNodeId(id: String): String {
            return "<node-transfer-$id>"
        }
    }
}

fun Node.resizeRelocate(bound: Rectangle2D) {
    this.resizeRelocate(bound.minX, bound.minY, bound.width, bound.height)
}