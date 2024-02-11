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
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DIVIDER_SIZE
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutLeaf
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode

class DragLayout : Pane() {

    var layoutRoot: DragLayoutNode = DragLayoutNode("root")

    private val dragCueShowing = SimpleBooleanProperty(false)
    private val drawCueRect: Pane = Pane()
    private val layoutLock = Object()
    private val layoutChangeListeners = mutableListOf<LayoutChangeListener>()

    init {
        setOnDragOver(this::onDragOver)
        setOnDragEntered { }
        setOnDragExited { dragCueShowing.value = false }
        setOnDragDropped(this::dropHandler)
        drawCueRect.visibleProperty().bind(dragCueShowing)
        drawCueRect.styleClass.add("drag-cue")
        styleClass.setAll("drag-layout")
        stylesheets.add("/styles/drag-layout.css")
    }

    fun addLayoutChangeListener(listener: LayoutChangeListener) {
        layoutChangeListeners.add(listener)
    }

    fun load(node: DragLayoutNode) {
        layoutRoot = node
        layoutRoot.addLayoutChangeListener {
            layoutChangeListeners.fire(layoutRoot)
        }
        updateChildren()
    }

    fun addComponent(comp: Node, id: String) {
        layoutRoot.insertNodes(0, mutableListOf(DragLayoutLeaf(component = comp, id = id)))
    }

    private fun onDragOver(e: DragEvent) {
        dragCueShowing.value = false
        val noteId = decodeNoteId(e.dragboard.string) ?: return
        val intersectedNode = layoutRoot.intersect(xyToAbsolute(Point2D(e.x, e.y)), getDividerMargin()) ?: return
        if (intersectedNode.boundCache == null) return
        if (noteId == intersectedNode.id) return

        e.acceptTransferModes(TransferMode.MOVE)
        var queBounds = intersectedNode.boundCache!!
        val side = intersectedNode.getSideSections().intersect(Point2D(e.x, e.y))
        if (side != null)
            queBounds = side.first

        drawCueRect.resizeRelocate(queBounds)
        dragCueShowing.value = true
    }

    private fun dropHandler(e: DragEvent) {
        try {
            val noteId = decodeNoteId(e.dragboard.string) ?: return
            val intersectedNode = layoutRoot.intersect(xyToAbsolute(Point2D(e.x, e.y)), getDividerMargin()) ?: return
            if (intersectedNode.boundCache == null) return
            if (noteId == intersectedNode.id) return

            val side = intersectedNode.getSideSections().intersect(Point2D(e.x, e.y))
            if (side != null) {
                splitSide(side.second, noteId, intersectedNode.id)
            } else {
                swapNodes(noteId, intersectedNode.id)
            }
            layoutChangeListeners.fire(layoutRoot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun swapNodes(target: String, dest: String) {
        val targetNode = layoutRoot.findComponentLeaf(target) ?: return
        val destNode = layoutRoot.findComponentLeaf(dest) ?: return
        targetNode.swapOnto(destNode)
        layoutChildren()
    }

    private fun splitSide(side: Side, source: String, target: String) {
        synchronized(layoutLock) {
            val sourceNode = layoutRoot.cutComponentLeaf(source) ?: return
            val targetNode = layoutRoot.findComponentLeaf(target) ?: return
            targetNode.insertAtSide(side, sourceNode)
            layoutRoot.simplify()
            layoutRoot.createDividers()
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
        layoutRoot.iterateComponents {
            it.node.setOnDragDetected { _ ->
                val db = it.node.startDragAndDrop(TransferMode.MOVE)
                val content = ClipboardContent()
                content.putString(encodeNodeId(it.nodeId))
                db.setContent(content)
            }
            this.children.add(it.node)
        }

        layoutRoot.iterateDividers {
            this.children.add(it.divider)
        }

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
            node.children[i].boundCache = childBounds[i]
            if (node.children[i].isComponent) {
                node.children[i].component!!.resizeRelocate(childBounds[i])
            } else if (node.children[i].isNode) {
                layoutNode(node.children[i].node!!, childBounds[i])
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

    private fun encodeNodeId(id: String): String {
        return "<node-transfer-$id>"
    }

    private fun decodeNoteId(encoded: String): String? {
        if (encoded.startsWith("<node-transfer-") && encoded.endsWith(">"))
            return encoded.substring(15, encoded.length - 1)
        return null
    }

    @FunctionalInterface
    fun interface LayoutChangeListener {
        fun change(node: DragLayoutNode)
    }
}

fun MutableList<DragLayout.LayoutChangeListener>.fire(e: DragLayoutNode) {
    this.forEach { it.change(e) }
}

fun Node.resizeRelocate(bound: Rectangle2D) {
    this.resizeRelocate(bound.minX, bound.minY, bound.width, bound.height)
}