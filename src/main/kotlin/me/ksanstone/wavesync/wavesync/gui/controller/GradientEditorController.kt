package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.beans.property.ObjectProperty
import javafx.beans.property.ObjectPropertyBase
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.fxml.Initializable
import javafx.geometry.Point2D
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.GradientSerializer
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.toAHexString
import java.net.URL
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.math.min

class GradientEditorController : Initializable {

    lateinit var colorPointer: Circle
    lateinit var colorSelectContainer: HBox
    lateinit var container: VBox
    lateinit var hexInput: TextField
    lateinit var alphaSlider: Slider
    lateinit var hueSLider: Slider
    lateinit var gradientPreview: Rectangle
    lateinit var shadeRect: Rectangle
    lateinit var colorRect: Rectangle
    lateinit var stopContainer: Pane
    lateinit var colorSelectStackPane: StackPane

    val readyGradient: ObjectProperty<SGradient> = SimpleObjectProperty(null)

    private val gradientSerializer = WaveSyncBootApplication.applicationContext.getBean(GradientSerializer::class.java)
    private val stops: ObservableList<Stop> = FXCollections.observableArrayList()

    val gradient: ObjectProperty<SGradient?> = object : ObjectPropertyBase<SGradient?>() {

        init {
            stops.addListener(ListChangeListener {
                while (it.next()) { /* ff updates */ }
                set(gradientSerializer.fromStops(stops).getOrNull())
            })
        }

        override fun get(): SGradient? {
            return gradientSerializer.fromStops(stops).getOrNull()
        }

        override fun set(newValue: SGradient?) {
            val newStops = newValue?.let { gradientSerializer.toStops(newValue) } ?: listOf()
            if (newStops != stops) {
                stops.setAll(newStops)
                fireValueChangedEvent()
            }
        }

        override fun getBean() = this@GradientEditorController
        override fun getName() = "GradientEditorController"
    }

    private val currentHue = SimpleDoubleProperty(0.0)
    private val currentSaturation = SimpleDoubleProperty(0.0)
    private val currentBrightness = SimpleDoubleProperty(0.0)
    private val currentAlpha = SimpleDoubleProperty(1.0)

    override fun initialize(p0: URL?, p1: ResourceBundle?) {
        shadeRect.widthProperty().bind(shadeRect.heightProperty())
        colorRect.widthProperty().bind(colorRect.heightProperty())
        colorRect.heightProperty().bind(shadeRect.heightProperty())

        colorSelectStackPane.prefWidthProperty().bind(container.widthProperty().subtract(40))
        colorSelectStackPane.prefHeightProperty().bind(colorSelectContainer.heightProperty())

        gradientPreview.widthProperty().bind(container.widthProperty())
        stops.addListener(ListChangeListener { updateGuiTickMarks(it) })

        alphaSlider.prefHeightProperty().bind(colorRect.heightProperty())
        hueSLider.prefHeightProperty().bind(colorRect.heightProperty())

        listOf(colorSelectStackPane.widthProperty(), colorSelectStackPane.heightProperty()).forEach {
            it.addListener { _ ->
                val m = min(colorSelectStackPane.width, colorSelectStackPane.height)
                shadeRect.heightProperty().set(m)
                moveCursor()
            }
        }

        listOf(
            currentSaturation,
            currentBrightness,
            shadeRect.heightProperty()
        ).forEach { it.addListener { _ -> moveCursor() } }

        currentHue.bind(hueSLider.valueProperty())
        currentAlpha.bind(alphaSlider.valueProperty().divide(100.0))
        currentHue.addListener { _, _, newValue ->
            colorRect.fill = LinearGradient(
                0.0, 0.0, 1.0, 0.0,
                true,
                CycleMethod.NO_CYCLE,
                Stop(0.0, Color.WHITE),
                Stop(1.0, Color.hsb(newValue.toDouble(), 1.0, 1.0))
            )
        }

        colorSelectStackPane.setOnMouseDragged {
            val p = colorRect.parentToLocal(it.x, it.y)
            currentSaturation.value = (p.x / colorRect.width).coerceIn(0.0, 1.0)
            currentBrightness.value = (1.0 - (p.y / colorRect.height)).coerceIn(0.0, 1.0)
        }

        listOf(currentHue, currentSaturation, currentBrightness, currentAlpha).forEach {
            it.addListener { _ ->
                colorUpdate()
                moveCursor()
            }
        }
    }

    private fun updateGuiTickMarks(it: ListChangeListener.Change<out Stop>?) {

    }

    private fun moveCursor() {
        val newPos = Point2D(
            colorRect.width * currentSaturation.value,
            colorRect.height * (1.0 - currentBrightness.value),
        )
        colorPointer.translateX = newPos.x - colorRect.width / 2
        colorPointer.translateY = newPos.y - colorRect.width / 2
    }

    private fun setColor(color: Color) {
        hueSLider.value = color.hue
        alphaSlider.value = color.opacity * 100
        moveCursor()
    }

    private fun colorUpdate() {
        val newColor = Color.hsb(currentHue.value, currentSaturation.value, currentBrightness.value, currentAlpha.value)
        hexInput.text = newColor.toAHexString()
    }


}