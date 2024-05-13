package me.ksanstone.wavesync.wavesync.gui.component.control

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.window.CustomizedStage.CaptionButton

class ControlBar : HBox() {

    private val maximize = "\uE922"
    private val restore = "\uE923"

    val minimizeButton = Button("\uE921").apply { styleClass.setAll("control-minimize", "caption-button") }
    val maximiseButton = Button(maximize).apply { styleClass.setAll("control-maximise", "caption-button") }
    val closeButton = Button("\uE8BB").apply { styleClass.setAll("control-close", "caption-button") }
    val drag = VBox().apply { setHgrow(this, Priority.ALWAYS) }

    private var maximizeRestore: Boolean = false
        set(value) {
            if (value)
                maximiseButton.text = maximize
            else
                maximiseButton.text = restore
            field = value
        }

    fun hoverButton(button: CaptionButton?) {
        minimizeButton.styleClass.remove("hover")
        maximiseButton.styleClass.remove("hover")
        closeButton.styleClass.remove("hover")
        when(button) {
            CaptionButton.CLOSE -> closeButton.styleClass.add("hover")
            CaptionButton.MINIMIZE -> minimizeButton.styleClass.add("hover")
            CaptionButton.MAXIMIZE_RESTORE -> maximiseButton.styleClass.add("hover")
            null -> {}
        }
    }

    fun setup() {
        val titleLabel = Label()
        val icon = ImageView((scene.window as Stage).icons[0]).apply { fitWidth = 16.0; fitHeight = 16.0 }
        val title = HBox().apply {
            styleClass.add("control-title")
            alignment = Pos.CENTER_LEFT
            spacing = 5.0
            padding = Insets(5.0)
            this.children.addAll(icon, titleLabel)
        }
        val caption = HBox().apply {
            children.addAll( minimizeButton, maximiseButton, closeButton)
            this.styleClass.add("caption")
        }
        children.addAll(title, drag, caption)
        this.styleClass.add("bar-height main-control-bar")
        (scene.window as? Stage)?.maximizedProperty()?.addListener { _, _, newValue -> maximizeRestore = !newValue}
        titleLabel.textProperty().bind((scene.window as Stage).titleProperty())
    }

}