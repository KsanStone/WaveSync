package me.ksanstone.wavesync.wavesync.gui.window

import javafx.scene.control.MenuBar
import javafx.scene.layout.HBox

class CaptionConfiguration(
    var captionHeight: Int = 31,
) {
    var dragRegion: DragRegion? = null

    /**
     * Specify a [DragRegion] to define where the window should be
     * draggable
     * @param captionDragRegion the [DragRegion]
     */
    fun setCaptionDragRegion(captionDragRegion: DragRegion?): CaptionConfiguration {
        this.dragRegion = captionDragRegion
        return this
    }

    /**
     * Specify a [MenuBar] to define where the window should be draggable
     * while excluding the buttons in the MenuBar
     * @param menuBar the [MenuBar]
     */
    fun setCaptionDragRegion(menuBar: MenuBar): CaptionConfiguration {
        // create new DragRegion with MenuBar
        val region = DragRegion(menuBar)
        // exclude all elements in MenuBar from DragRegion
        val box = menuBar.childrenUnmodifiable[0] as HBox
        for (node in box.childrenUnmodifiable) {
            region.addExcludeBounds(node)
        }
        this.dragRegion = region
        return this
    }
}