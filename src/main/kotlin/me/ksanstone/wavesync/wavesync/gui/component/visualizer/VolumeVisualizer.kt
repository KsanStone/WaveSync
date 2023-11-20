package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.css.CssMetaData
import javafx.css.Styleable
import javafx.css.StyleableProperty
import javafx.css.StyleablePropertyFactory
import javafx.geometry.Orientation
import javafx.geometry.VPos
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import java.text.DecimalFormat

class VolumeVisualizer : AutoCanvas(false) {

    override fun getCssMetaData(): List<CssMetaData<out Styleable?, *>> {
        return FACTORY.cssMetaData
    }
    companion object {
        private val FACTORY: StyleablePropertyFactory<VolumeVisualizer> =
            StyleablePropertyFactory<VolumeVisualizer>(
                Pane.getClassCssMetaData()
            )

        @Suppress("unused")
        fun getClassCssMetaData(): List<CssMetaData<out Styleable?, *>> {
            return FACTORY.cssMetaData
        }
    }

    private val peakColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "peakColor", "-fx-peak-color") { vis -> vis.peakColor }
    private val bottomColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "bottomColor", "-fx-bottom-color") { vis -> vis.bottomColor }
    private val tickColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "tickColor", "-fx-tick-color") { vis -> vis.tickColor }

    val orientationProperty = SimpleObjectProperty<Orientation>()
    val valueProperty = SimpleDoubleProperty(Double.MIN_VALUE)
    val rangeMin = SimpleDoubleProperty(-80.0)
    val rangeMax = SimpleDoubleProperty(0.0)
    val tickUnit = SimpleDoubleProperty(10.0)
    val tickMarks = FXCollections.observableArrayList<Pair<Double, String>>()
    val range = rangeMax.subtract(rangeMin)

    private val tickFormat = DecimalFormat()

    init {
        canvasContainer.xAxisShown.value = false
        canvasContainer.yAxisShown.value = false
        canvasContainer.tooltipEnabled.value = false
        canvasContainer.verticalLinesVisible.value = false
        canvasContainer.horizontalLinesVisible.value = false

        this.styleClass.setAll("volume-visualizer")
        this.stylesheets.add("/styles/volume-visualizer.css")

        orientationProperty.addListener { _, _, orientation ->
            when(orientation!!) {
                Orientation.HORIZONTAL -> {prefWidth = 300.0; prefHeight = 25.0}
                Orientation.VERTICAL -> {prefWidth = 25.0; prefHeight = 300.0}
            }
        }
        orientationProperty.set(Orientation.HORIZONTAL)

        listOf(rangeMax, rangeMin, tickUnit).forEach { it.addListener { _ -> calculateTicks() } }
        calculateTicks()
    }

    private fun calculateTicks() {
        val newTickMarks = sortedSetOf<Double>()
        val max = rangeMax.get()
        val min = rangeMin.get()
        val tickUnit = tickUnit.get()

        val delta = tickUnit * 0.5
        var tick = min - min % tickUnit + tickUnit
        while (tick < max) { newTickMarks.add(tick); tick += tickUnit}
        newTickMarks.addAll(listOf(min, max))
        val tickMarkList = newTickMarks.toMutableList()
        if (tickMarkList.size > 2 && tickMarkList[1] - tickMarkList[0] < delta) tickMarkList.removeAt(1)
        if (tickMarkList.size > 2 && tickMarkList[tickMarkList.size - 1] - tickMarkList[tickMarkList.size - 2] < delta)
            tickMarkList.removeAt(tickMarkList.size - 2)

        tickMarks.setAll(tickMarkList.map { it to tickFormat.format(it) })
    }

    private fun getTickPosition(tick: Double): Double {
        return ((tick - rangeMin.get()) / range.get()) *
                when (orientationProperty.get()!!) {Orientation.HORIZONTAL -> width - 1; Orientation.VERTICAL -> height - 1}
    }

    private fun getTextAlign(tickPosition: Double, textWidth: Double): TextAlignment {
        val alignFactor = when (orientationProperty.get()!!) {Orientation.HORIZONTAL -> width; Orientation.VERTICAL -> height}
        if (tickPosition < textWidth) return TextAlignment.LEFT
        if (tickPosition > alignFactor - textWidth) return TextAlignment.RIGHT
        return TextAlignment.CENTER
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        val max = rangeMax.get()
        val min = rangeMin.get()
        val range = range.get()
        val v = (valueProperty.get() - min).coerceIn(0.0, range) / range
        val orientation = orientationProperty.get()

        // Drawing the bar
        gc.clearRect(0.0, 0.0, width, height)
        val stops = arrayOf(Stop(0.0, bottomColor.value), Stop(1.0, peakColor.value))
        when (orientation!!) {
            Orientation.HORIZONTAL -> {
                gc.fill = LinearGradient(
                    0.0, 0.0, width, 0.0,
                    false, CycleMethod.NO_CYCLE, *stops
                )
                gc.fillRect(0.0, 0.0, width * v, height)
            }
            Orientation.VERTICAL -> {
                gc.fill = LinearGradient(
                    0.0, height, 0.0, 0.0,
                    false, CycleMethod.NO_CYCLE, *stops
                )
                gc.fillRect(0.0, height * (1.0 - v), width, height * v)
            }
        }

        // Drawing the tick marks on top
        gc.stroke = tickColor.value
        gc.fill = tickColor.value
        gc.textBaseline = VPos.CENTER
        val tickSize = 5.0
        when (orientation) {
            Orientation.HORIZONTAL -> {
                tickMarks.forEach {
                    val pos = getTickPosition(it.first)
                    gc.strokeLine(pos, 0.0, pos, tickSize)
                    gc.strokeLine(pos, height - tickSize, pos, height)
                    gc.textAlign = getTextAlign(pos, 5.0)
                    gc.fillText(it.second, pos, height / 2)
                }
            }
            Orientation.VERTICAL -> {

            }
        }
    }
}