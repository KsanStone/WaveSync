package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.css.CssMetaData
import javafx.css.Styleable
import javafx.css.StyleableProperty
import javafx.css.StyleablePropertyFactory
import javafx.geometry.Bounds
import javafx.geometry.Orientation
import javafx.geometry.VPos
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.smoothing.MultiplicativeSmoother
import me.ksanstone.wavesync.wavesync.utility.ChannelLabel
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
    private val tickLabelFillColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(
            this,
            "tickLabelFill",
            "-fx-tick-label-fill"
        ) { vis -> vis.tickLabelFillColor }

    val orientationProperty = SimpleObjectProperty<Orientation>()
    var values: List<Double>
        get() = FXCollections.unmodifiableObservableList(valueProperty) as ObservableList<Double>
        set(value) {
            synchronized(valueProperty) {
                valueProperty.setAll(value)
            }
        }

    var labels: List<ChannelLabel>
        get() = FXCollections.unmodifiableObservableList(labelProperty) as ObservableList<ChannelLabel>
        set(value) {
            synchronized(labelProperty) {
                labelProperty.setAll(value)
            }
        }

    val rangeMin = SimpleDoubleProperty(-90.0)
    val rangeMax = SimpleDoubleProperty(0.0)
    val tickUnit = SimpleDoubleProperty(10.0)
    val tickMarks = FXCollections.observableArrayList<Pair<Double, String>>()
    val range = rangeMax.subtract(rangeMin)

    private val valueProperty = FXCollections.observableArrayList<Double>()
    private val labelProperty = FXCollections.observableArrayList<ChannelLabel>()
    private val tickFormat = DecimalFormat()
    private val smoother = MultiplicativeSmoother()

    init {
        smoother.factor = 0.3
        smoother.dataSize = 1
        smoother.boundMin = rangeMin.get().toFloat()
        rangeMin.addListener { _, _, v -> smoother.boundMin = v.toFloat() }
        smoother.boundMax = rangeMax.get().toFloat()
        rangeMax.addListener { _, _, v -> smoother.boundMax = v.toFloat() }


        canvasContainer.xAxisShown.value = false
        canvasContainer.yAxisShown.value = false
        canvasContainer.tooltipEnabled.value = false
        canvasContainer.verticalLinesVisible.value = false
        canvasContainer.horizontalLinesVisible.value = false

        this.styleClass.setAll("volume-visualizer")
        this.stylesheets.add("/styles/volume-visualizer.css")

        orientationProperty.addListener { _, _, orientation ->
            when (orientation!!) {
                Orientation.HORIZONTAL -> {
                    prefWidth = 300.0; prefHeight = 25.0
                }

                Orientation.VERTICAL -> {
                    prefWidth = 25.0; prefHeight = 300.0
                }
            }
        }
        orientationProperty.set(Orientation.HORIZONTAL)
        valueProperty.addListener(ListChangeListener { c ->
            while (c.next()) { /* wait for changes to settle */
            }
            if (smoother.dataSize != valueProperty.size)
                smoother.dataSize = valueProperty.size
            valueProperty.forEachIndexed { i, it -> smoother.dataTarget[i] = it.toFloat() }
        })

        listOf(rangeMax, rangeMin, tickUnit).forEach { it.addListener { _ -> calculateTicks() } }
        calculateTicks()
    }

    private fun calculateTicks() {
        val newTickMarks = sortedSetOf<Double>()
        val max = rangeMax.get()
        val min = rangeMin.get()
        val tickUnit = tickUnit.get()

        var tick = min - min % tickUnit + tickUnit
        while (tick < max) {
            newTickMarks.add(tick); tick += tickUnit
        }
        newTickMarks.addAll(listOf(min, max))
        val tickMarkList = newTickMarks.toMutableList()

        tickMarks.setAll(tickMarkList.map { it to tickFormat.format(it) })
    }

    private fun getTickPosition(tick: Double, s: Double): Double {
        return ((tick - rangeMin.get()) / range.get()) *
                when (orientationProperty.get()!!) {
                    Orientation.HORIZONTAL -> s - 1; Orientation.VERTICAL -> s - 1
                }
    }

    private fun getTextAlign(tickPosition: Double, textWidth: Double, offset: Double): Double {
        val alignFactor = when (orientationProperty.get()!!) {
            Orientation.HORIZONTAL -> width - offset; Orientation.VERTICAL -> height - offset
        }
        if (tickPosition < textWidth + offset) return 1.0
        if (tickPosition > alignFactor - textWidth) return 0.0
        return 0.5
    }

    private fun measureText(t: String, font: Font): Bounds {
        val text = Text(t)
        text.font = font
        return text.boundsInLocal
    }

    private fun shouldDrawLabels(size: Double, measures: List<Double>): Boolean {
        return labelProperty.size == valueProperty.size &&
                measures.stream().map { it + 2 }.reduce(0.0) { a, b -> a + b } <= size
    }

    private fun measureLabels(size: Double, font: Font): List<Triple<Bounds, Double, String>> {
        val f = size / labelProperty.size.toDouble()
        return labelProperty.mapIndexed { i, it ->
            return@mapIndexed Triple(measureText(it.shortcut, font), f * (i.toDouble() + 0.5), it.shortcut)
        }
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        smoother.applySmoothing(deltaT)
        var w = width
        var h = height
        val min = rangeMin.get()
        val range = range.get()
        val orientation = orientationProperty.get()
        val shortcutFont = Font.font(12.0)
        var dataCopy: Array<Double>
        var drawShortcuts: Boolean
        var shortcuts: List<Triple<Bounds, Double, String>>
        var barOffset: Double = 0.0

        synchronized(valueProperty) {
            dataCopy = valueProperty.toTypedArray()
        }

        synchronized(labelProperty) {
            val size = when (orientation!!) {
                Orientation.VERTICAL -> width
                Orientation.HORIZONTAL -> height
            }
            shortcuts = measureLabels(size, shortcutFont)
            drawShortcuts = shouldDrawLabels(size, shortcuts.map {
                when (orientation) {
                    Orientation.VERTICAL -> it.first.width
                    Orientation.HORIZONTAL -> it.first.height
                }
            })
        }

        if (drawShortcuts) {
            barOffset = shortcuts.maxOf {
                when (orientation!!) {
                    Orientation.VERTICAL -> it.first.height
                    Orientation.HORIZONTAL -> it.first.width
                }
            } + 2.0
            when (orientation!!) {
                Orientation.VERTICAL -> h -= barOffset
                Orientation.HORIZONTAL -> w -= barOffset
            }
        }

        // Drawing the bar
        gc.clearRect(0.0, 0.0, width, height)
        val stops = arrayOf(Stop(0.0, bottomColor.value), Stop(1.0, peakColor.value))
        when (orientation!!) {
            Orientation.HORIZONTAL -> {
                val sliceSize = h / dataCopy.size
                for ((i, d) in dataCopy.withIndex()) {
                    val v = (d - min).coerceIn(0.0, range) / range
                    gc.fill = LinearGradient(
                        barOffset, 0.0, w, 0.0,
                        false, CycleMethod.NO_CYCLE, *stops
                    )
                    gc.fillRect(barOffset, sliceSize * i, w * v, sliceSize)
                }
            }

            Orientation.VERTICAL -> {
                val sliceSize = w / dataCopy.size
                for ((i, d) in dataCopy.withIndex()) {
                    val v = (d - min).coerceIn(0.0, range) / range
                    gc.fill = LinearGradient(
                        0.0, h, 0.0, 0.0,
                        false, CycleMethod.NO_CYCLE, *stops
                    )
                    gc.fillRect(sliceSize * i, h * (1.0 - v), sliceSize, h * v)
                }
            }
        }

        // Drawing the tick marks on top
        gc.stroke = tickColor.value
        gc.fill = tickLabelFillColor.value
        gc.font = Font.font(10.0)
        val tickSize = 5.0
        val tickPad = 3.0
        when (orientation) {
            Orientation.HORIZONTAL -> {
                gc.textBaseline = VPos.CENTER
                var previousText = 0.0
                val lastText = measureText(tickMarks.last().second, gc.font).width
                tickMarks.forEachIndexed { i, it ->
                    val pos = getTickPosition(it.first, w) + barOffset
                    gc.strokeLine(pos, 0.0, pos, tickSize)
                    gc.strokeLine(pos, h - tickSize, pos, h)

                    val measure = measureText(it.second, gc.font)
                    val align = getTextAlign(pos, 5.0, barOffset)
                    val notObstructingLast = pos + measure.width * align < w - lastText - tickPad
                    val notObstructingPrevious = previousText < pos - (measure.width * (1.0 - align)) - tickPad

                    if (i == 0 || i == tickMarks.size - 1 || (notObstructingPrevious && notObstructingLast)) {
                        gc.textAlign = when (align) {
                            1.0 -> TextAlignment.LEFT; 0.0 -> TextAlignment.RIGHT; else -> TextAlignment.CENTER; }
                        gc.fillText(it.second, pos, h / 2)
                        previousText = pos + measure.width * align
                    }
                }
            }

            Orientation.VERTICAL -> {
                gc.textAlign = TextAlignment.CENTER
                var previousText = 0.0
                val lastText = measureText(tickMarks.last().second, gc.font).height
                tickMarks.forEachIndexed { i, it ->
                    val pos = getTickPosition(it.first, h)
                    gc.strokeLine(0.0, pos, tickSize, pos)
                    gc.strokeLine(w - tickSize, pos, w, pos)

                    val measure = measureText(it.second, gc.font)
                    val align = getTextAlign(pos, 5.0, barOffset)
                    val notObstructingLast = pos + measure.height * align < h - lastText - tickPad
                    val notObstructingPrevious = previousText < pos - (measure.height * (1.0 - align)) - tickPad

                    if (i == 0 || i == tickMarks.size - 1 || (notObstructingPrevious && notObstructingLast)) {
                        gc.textBaseline = when (align) {
                            1.0 -> VPos.TOP; 0.0 -> VPos.BOTTOM; else -> VPos.CENTER; }
                        gc.fillText(it.second, w / 2, pos)
                        previousText = pos + measure.height * align
                    }
                }
            }
        }

        // Drawing channel labels
        if (!drawShortcuts) return
        gc.font = shortcutFont
        gc.fill = tickLabelFillColor.value
        when (orientation) {
            Orientation.HORIZONTAL -> {
                gc.textAlign = TextAlignment.LEFT
                gc.textBaseline = VPos.CENTER
                shortcuts.forEach { pair ->
                    gc.fillText(pair.third, 0.0, pair.second)
                }
            }

            Orientation.VERTICAL -> {
                gc.textAlign = TextAlignment.CENTER
                gc.textBaseline = VPos.BOTTOM
                shortcuts.forEach { pair ->
                    gc.fillText(pair.third, pair.second, height)
                }
            }
        }
    }
}