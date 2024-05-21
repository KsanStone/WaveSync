package me.ksanstone.wavesync.wavesync.gui.component.control

import javafx.scene.layout.VBox
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.window.CustomizedStage.CaptionButton
import me.ksanstone.wavesync.wavesync.gui.window.DragRegion

class MainControl : VBox() {

    val controls = ControlBar()
    private var isSetup = false

    init {

        this.children.addAll(controls)
        this.stylesheets.add("/styles/control.css")
    }

    fun setup(stage: Stage) {
        if (isSetup) return
        isSetup = true
        this.controls.setup()
        stage.fullScreenProperty().addListener { _, _, v ->
            if(v)
                children.remove(controls)
            else
                children.add(0, controls)
        }
    }

    fun hoverButton(button: CaptionButton?) {
        this.controls.hoverButton(button)
    }

    val dragRegion: DragRegion
        get() {
            return DragRegion(controls.drag)
        }
}