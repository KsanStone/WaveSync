package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.fxml.Initializable
import javafx.geometry.Point2D
import javafx.scene.control.Button
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
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
import kotlin.math.abs
import kotlin.math.min

class GradientEditorController : Initializable {

    lateinit var colorPreviewRect: Rectangle
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
                while (it.next()) { /* ff updates */
                }
                try {
                    set(gradientSerializer.fromStops(stops).getOrNull())
                } catch (ignored: Exception) {}
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

    private var activeButton = SimpleIntegerProperty(-1)

    override fun initialize(p0: URL?, p1: ResourceBundle?) {
        shadeRect.widthProperty().bind(shadeRect.heightProperty())
        colorRect.widthProperty().bind(colorRect.heightProperty())
        colorRect.heightProperty().bind(shadeRect.heightProperty())

        colorSelectStackPane.prefWidthProperty().bind(container.widthProperty().subtract(40))
        stopContainer.prefWidthProperty().bind(container.widthProperty())
        colorSelectStackPane.prefHeightProperty().bind(colorSelectContainer.heightProperty())

        stopContainer.setOnMousePressed {
            for (i in stopContainer.children.indices) {
                val child = stopContainer.children[i]
                if (child.boundsInParent.contains(it.x, it.y)) {
                    setActive((child as StopButton).index)
                    return@setOnMousePressed
                }
            }
            val newLoc = ((it.x - STOP_GRAB_WIDTH / 2) / gradientPreview.width).coerceIn(0.0, 1.0)
            addStop(newLoc)
        }

        stopContainer.setOnMouseDragged { event ->
            val newLoc = ((event.x - STOP_GRAB_WIDTH / 2) / gradientPreview.width).coerceIn(0.0, 1.0)

            val activeIndex = activeButton.value
            val activeStop = stops[activeIndex]
            val newStop = Stop(newLoc, activeStop.color)

            val temp = stops[activeIndex]

            stops.removeAt(activeIndex)
            val newIndex = findInsertIndex(newLoc)
            if (newIndex == -1 || stops[newIndex.coerceAtMost(stops.size - 1)].offset == newLoc) {
                stops.add(activeIndex, temp)
                return@setOnMouseDragged
            }

            stops.add(newIndex, newStop)

            val updatedIndex = stops.indexOf(newStop)
            setActive(updatedIndex)
        }

        gradientPreview.widthProperty().bind(container.widthProperty().subtract(STOP_GRAB_WIDTH))
        stops.addListener(ListChangeListener { it.next(); updateGuiTickMarks(); colorRect() })

        alphaSlider.prefHeightProperty().bind(colorRect.heightProperty())
        hueSLider.prefHeightProperty().bind(colorRect.heightProperty())

        container.widthProperty().addListener { _, _, _ -> updateGuiTickMarks() }

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

        colorUpdate()
        updateGuiTickMarks()
        colorRect()
        if(stops.size > 0)
            setActive(0)
    }

    private fun updateGuiTickMarks() {
        val diff = stops.size - stopContainer.children.size
        if (diff > 0)
            for (i in 0 until diff) stopContainer.children.add(StopButton(this))
        else if (diff < 0)
            for (i in 0 until abs(diff)) stopContainer.children.removeLast()

        for (i in stops.indices) {
            val loc = Point2D(
                gradientPreview.widthProperty().value * stops[i].offset,
                0.0
            )

            stopContainer.children[i].relocate(loc.x, loc.y)
            (stopContainer.children[i] as StopButton).update(i, stops[i].color)
        }
    }

    private fun findInsertIndex(offset: Double): Int {
        if (stops.any { it.offset == offset }) return -1
        var insertIndex = stops.indexOfFirst { it.offset > offset }
        if (insertIndex == -1) insertIndex = stops.size
        return insertIndex
    }

    private fun addStop(offset: Double) {
        if (offset < 0 || offset > 1) return
        val insertIndex = findInsertIndex(offset)
        if(insertIndex == -1) return
        stops.add(insertIndex, Stop(offset, gradient.value!![offset.toFloat()]))
        setActive(insertIndex)
    }

    private fun deleteStop(index: Int) {
        if (!stops.indices.contains(index) || stops.size <= 2) return
        stops.removeAt(index)
    }

    private fun setActive(index: Int) {
        if (!stops.indices.contains(index)) return
        activeButton.set(index)
        stopContainer.children[index].requestFocus()
        stopContainer.children[index].toFront()
        setColor(stops[index].color)
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
        currentBrightness.value = color.brightness
        currentSaturation.value = color.saturation
        colorPreviewRect.fill = color
        moveCursor()
    }

    private fun colorUpdate() {
        val newColor = Color.hsb(currentHue.value, currentSaturation.value, currentBrightness.value, currentAlpha.value)
        hexInput.text = newColor.toAHexString()
        colorPreviewRect.fill = newColor
        if (stops.indices.contains(activeButton.value)) {
            stops[activeButton.value] = Stop(stops[activeButton.value].offset, newColor)
        }
    }

    private fun colorRect() {
        gradientPreview.fill = LinearGradient(0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE, stops)
    }

    class StopButton(private val controller: GradientEditorController) : Button() {

        var index = 0

        init {
            this.minWidth = STOP_GRAB_WIDTH
            this.maxWidth = STOP_GRAB_WIDTH
            this.prefWidth = STOP_GRAB_WIDTH
            controller.activeButton.addListener { _ -> updateBorder() }
            this.setOnAction {
                controller.setActive(index)
            }
            this.setOnKeyPressed {
                if (it.code == KeyCode.DELETE)
                    controller.deleteStop(index)
            }
            this.isMouseTransparent = true
        }

        fun update(index: Int, color: Color) {
            this.index = index
            updateBorder()
            this.style = "-fx-background-color: ${color.toAHexString()}"
        }

        private fun updateBorder() {
            if (controller.activeButton.value == index)
                this.border = Border(
                    BorderStroke(
                        controller.stops[index].color.invert(),
                        BorderStrokeStyle.SOLID,
                        CornerRadii(5.0),
                        null
                    )
                )
            else
                this.border =  Border(BorderStroke(Color.TRANSPARENT, BorderStrokeStyle.SOLID, CornerRadii(5.0), null))
        }

    }

    companion object {
        const val STOP_GRAB_WIDTH: Double = 20.0
    }
}