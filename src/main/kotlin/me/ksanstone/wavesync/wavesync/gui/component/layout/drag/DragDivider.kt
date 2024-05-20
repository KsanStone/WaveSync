package me.ksanstone.wavesync.wavesync.gui.component.layout.drag

import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.scene.Cursor
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode

class DragDivider(
    private val orientation: Orientation, private val parent: DragLayoutNode, private val dividerId: Int
) : Region() {

    private val cue: Pane = Pane()

    init {
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
        cue.maxWidth = Double.MAX_VALUE
        cue.maxHeight = Double.MAX_VALUE
        cue.isManaged = false

        cursor = when (orientation) {
            Orientation.HORIZONTAL -> Cursor.V_RESIZE
            Orientation.VERTICAL -> Cursor.H_RESIZE
        }

        styleClass.setAll("drag-divider")
        children.add(cue)
        cue.toFront()
        cue.styleClass.add("drag-divider-cue")

        setOnMouseDragged {
            val local = parent.getEffectiveLayout().get()!!.sceneToLocal(Point2D(it.sceneX, it.sceneY))
            val diff = when (orientation) {
                Orientation.HORIZONTAL -> local.y - parent.boundCache!!.minY
                Orientation.VERTICAL -> local.x - parent.boundCache!!.minX
            }

            parent.relocateDivider(dividerId, diff)
        }

        listOf(widthProperty(), heightProperty()).forEach { it.addListener { _ -> layoutCue() } }
    }

    private val cueSize = 30.0

    override fun requestLayout() {
        super.requestLayout()
        layoutCue()
    }

    private fun layoutCue() {
        when (orientation) {
            Orientation.HORIZONTAL -> {
                cue.resizeRelocate(width / 2 - cueSize / 2, 0.0, cueSize, height)
            }

            Orientation.VERTICAL -> {
                cue.resizeRelocate(0.0, height / 2 - cueSize / 2, width, cueSize)
            }
        }
    }
}