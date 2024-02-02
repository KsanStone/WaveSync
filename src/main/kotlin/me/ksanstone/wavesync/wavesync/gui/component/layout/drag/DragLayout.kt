package me.ksanstone.wavesync.wavesync.gui.component.layout.drag

import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Orientation
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode

class DragLayout : Pane() {

    private val dragCueShowing = SimpleBooleanProperty(false)
    val layoutRoot: DragLayoutNode = DragLayoutNode("root")

    init {
        setOnDragEntered { dragCueShowing.value = true }
        setOnDragExited { dragCueShowing.value = false }

        listOf(widthProperty(), heightProperty()).forEach {
            it.addListener { _ -> doLayout() }
        }
    }

    private fun doLayout() {

    }

}