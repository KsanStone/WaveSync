package me.ksanstone.wavesync.wavesync.gui.component.layout.drag

import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DIVIDER_SIZE
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode

class DragLayout : Pane() {

    private val dragCueShowing = SimpleBooleanProperty(false)
    private val drawCueRect: Rectangle = Rectangle()
    val layoutRoot: DragLayoutNode = DragLayoutNode("root")

    init {
        setOnDragOver(this::onDragOver)
        setOnDragEntered {  }
        setOnDragExited { dragCueShowing.value = false }
        setOnDragDropped(this::dropHandler)
        drawCueRect.visibleProperty().bind(dragCueShowing)
        drawCueRect.fill = Color.FUCHSIA.let { Color(it.red, it.green, it.blue, 0.5) }
    }

    private fun onDragOver(e: DragEvent) {
        dragCueShowing.value = false
        val noteId = decodeNoteId(e.dragboard.string) ?: return
        val intersectedNode = layoutRoot.intersect(xyToAbsolute(Point2D(e.x, e.y)), getDividerMargin()) ?: return
        if (intersectedNode.boundCache == null) return
        if (noteId == intersectedNode.id) return

        e.acceptTransferModes(TransferMode.MOVE)
        drawCueRect.width = intersectedNode.boundCache!!.width
        drawCueRect.height = intersectedNode.boundCache!!.height
        drawCueRect.resizeRelocate(intersectedNode.boundCache!!)
        dragCueShowing.value = true
    }

    private fun dropHandler(e: DragEvent) {
        val noteId = decodeNoteId(e.dragboard.string) ?: return
        val intersectedNode = layoutRoot.intersect(xyToAbsolute(Point2D(e.x, e.y)), getDividerMargin()) ?: return
        if (intersectedNode.boundCache == null) return
        if (noteId == intersectedNode.id) return

        swapNodes(noteId, intersectedNode.id)
    }

    private fun swapNodes(target: String, dest: String) {
        val targetNode = layoutRoot.findComponentLeaf(target) ?: return
        val destNode = layoutRoot.findComponentLeaf(dest) ?: return
        targetNode.swapOnto(destNode)
        layoutChildren()
    }

    /**
     * Ensures all the layout child nodes are added to this component's children list
     */
    fun updateChildren() {
        this.children.clear()
        layoutRoot.createDividers()
        layoutRoot.iterateComponents {
            it.node.setOnDragDetected { me ->
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

    override fun layoutChildren() {
        layoutNode(layoutRoot, Rectangle2D(0.0, 0.0, width, height))
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
}

fun Node.resizeRelocate(bound: Rectangle2D) {
    this.resizeRelocate(bound.minX, bound.minY, bound.width, bound.height)
}