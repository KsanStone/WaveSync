package me.ksanstone.wavesync.wavesync.gui.component.layout.drag

import javafx.geometry.Orientation
import javafx.scene.Cursor
import javafx.scene.layout.Background
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode

class DragDivider(
    orientation: Orientation,
    parent: DragLayoutNode,
    dividerId: Int
) : Region() {

    init {
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE

        background = Background.fill(Color.BLACK)
        cursor = when (orientation) {
            Orientation.HORIZONTAL -> Cursor.H_RESIZE
            Orientation.VERTICAL -> Cursor.V_RESIZE
        }

        setOnMouseDragged {
            val diff = when (orientation) {
                Orientation.HORIZONTAL -> it.sceneX - parent.boundCache!!.minX
                Orientation.VERTICAL -> it.sceneY - parent.boundCache!!.minY
            }

            parent.relocateDivider(dividerId, diff)
        }
    }
}