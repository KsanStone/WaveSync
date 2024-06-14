package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.Initializable
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import java.net.URL
import java.util.*
import kotlin.math.min

class GradientEditorController: Initializable {
    lateinit var hexInput: TextField
    lateinit var alphaSlider: Slider
    lateinit var hueSLider: Slider
    lateinit var gradientPreview: Rectangle
    lateinit var shadeRect: Rectangle
    lateinit var colorRect: Rectangle
    lateinit var stopContainer: Pane
    lateinit var colorSelectStackPane: StackPane

    val readyGradient: ObjectProperty<SGradient> = SimpleObjectProperty(null)
    override fun initialize(p0: URL?, p1: ResourceBundle?) {
        shadeRect.widthProperty().bind(shadeRect.heightProperty())
        colorRect.widthProperty().bind(colorRect.heightProperty())
        colorRect.heightProperty().bind(shadeRect.heightProperty())

        listOf(colorSelectStackPane.widthProperty(), colorSelectStackPane.heightProperty()).forEach { it.addListener { _ ->
            val m = min(colorSelectStackPane.width, colorSelectStackPane.height)
            shadeRect.heightProperty().set(m)
        } }
    }


}