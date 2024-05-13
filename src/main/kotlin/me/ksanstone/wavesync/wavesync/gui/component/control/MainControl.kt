package me.ksanstone.wavesync.wavesync.gui.component.control

import javafx.scene.layout.VBox
import me.ksanstone.wavesync.wavesync.gui.window.CustomizedStage.CaptionButton
import me.ksanstone.wavesync.wavesync.gui.window.DragRegion

class MainControl : VBox() {

    val controls = ControlBar()

    init {
        this.children.addAll(controls)
        this.stylesheets.add("/styles/control.css")
    }

    fun setup() {
        this.controls.setup()
    }

    fun hoverButton(button: CaptionButton?) {
        this.controls.hoverButton(button)
    }

    val dragRegion: DragRegion
        get() {
            return DragRegion(controls.drag)
        }
}