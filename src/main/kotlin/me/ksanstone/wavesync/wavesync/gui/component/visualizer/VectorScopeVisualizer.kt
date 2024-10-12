package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.*
import javafx.css.CssMetaData
import javafx.css.Styleable
import javafx.css.StyleableProperty
import javafx.css.StyleablePropertyFactory
import javafx.fxml.FXMLLoader
import javafx.geometry.Point2D
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_VECTOR_MODE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_VECTOR_RANGE_LINK
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_VECTOR_X_RANGE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_VECTOR_Y_RANGE
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.vector.VectorSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class VectorScopeVisualizer : AutoCanvas(false) {

    companion object {

        private val ROOT2 = sqrt(2.0)

        private val FACTORY: StyleablePropertyFactory<VectorScopeVisualizer> =
            StyleablePropertyFactory<VectorScopeVisualizer>(
                Pane.getClassCssMetaData()
            )

        @Suppress("unused")
        fun getClassCssMetaData(): List<CssMetaData<out Styleable?, *>> {
            return FACTORY.cssMetaData
        }
    }

    private val acs = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
    private val vectorColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "vectorColor", "-fx-color") { vis -> vis.vectorColor }

    val renderMode: ObjectProperty<VectorOrientation> = SimpleObjectProperty(DEFAULT_VECTOR_MODE)
    val rangeX: DoubleProperty = SimpleDoubleProperty(DEFAULT_VECTOR_X_RANGE)
    val rangeY: DoubleProperty = SimpleDoubleProperty(DEFAULT_VECTOR_Y_RANGE)
    val rangeLink: BooleanProperty = SimpleBooleanProperty(DEFAULT_VECTOR_RANGE_LINK)

    private var edgePoints: List<Pair<Double, Double>> = emptyList()

    init {
        updateAxis()
        listOf(rangeX, rangeY, renderMode).forEach { it.addListener { _, _, _ -> updateAxis() } }

        styleClass.add("vector-visualizer")
        stylesheets.add("/styles/waveform-visualizer.css")


        val num = 24
        val step = Math.PI / num * 2
        val nums = mutableListOf<Pair<Double, Double>>()

        for (i in 0 until num) {
            val a = i * step
            val cos = cos(a)
            val sin = sin(a)

            if (a < Math.PI / 4 || a >= Math.PI * 1.75) {
                nums.add(1.0 to sin * (1 / cos))
            } else if (a >= Math.PI / 4 && a < Math.PI * 0.75) {
                nums.add(cos * (1 / sin) to 1.0)
            } else if (a >= Math.PI * 0.75 && a < Math.PI * 1.25) {
                nums.add(-1.0 to -sin * (1 / cos))
            } else if (a >= Math.PI * 1.25 && a < Math.PI * 1.75) {
                nums.add(-cos * (1 / sin) to -1.0)
            }
        }

        edgePoints = nums
    }

    private fun updateAxis() {
        xAxis.lowerBound = -rangeX.value
        xAxis.upperBound = rangeX.value
        (xAxis as NumberAxis).tickUnit = 0.1
        yAxis.lowerBound = -rangeY.value
        yAxis.upperBound = rangeY.value
        (yAxis as NumberAxis).tickUnit = 0.1
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)
        gc.stroke = vectorColor.value
        gc.beginPath()
        if (acs.samples.channels() == 2 + 1) { // combined + L + R
            when (renderMode.value!!) {
                VectorOrientation.SKEWED -> drawSkewed(gc, width, height)
                VectorOrientation.STRAIGTH -> drawVertical(gc, width, height)
            }
        }
    }

    private fun drawSkewed(gc: GraphicsContext, width: Double, height: Double) {
        val sX = 1.0 / rangeX.value
        val sY = 1.0 / rangeY.value

        for (i in 0 until acs.samples.sizeHint(0)) {
            val sample = acs.samples[1].data[i]
            val p = Point2D(
                (sample.toDouble() * sX + 1.0) * 0.5 * width - 1.0,
                height - (acs.samples[2].data[i].toDouble() * sY + 1) * height * 0.5
            )
            if (i == 0) {
                gc.moveTo(p.x, p.y)
            } else {
                gc.lineTo(p.x, p.y)
            }
        }

        gc.closePath()
        gc.stroke()
    }

    private fun rotate45(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        xOffset: Double,
        yOffset: Double
    ): Point2D {
        val sumHalved = (x + y) * 0.5
        val x2 = ROOT2 * (x - sumHalved)
        val y2 = ROOT2 * sumHalved
        return Point2D(x2 * width + xOffset, height - y2 * height + yOffset)
    }


    private fun drawVertical(gc: GraphicsContext, width: Double, height: Double) {
        val sX = 1.0 / rangeX.value
        val sY = 1.0 / rangeY.value
        val dividedWidth = (width - 4) / 2 / ROOT2 * sX
        val dividedHeight = height / 2 / ROOT2
        val scaledHeight = dividedHeight * sY
        val yOffset = (ROOT2 - 1) * scaledHeight - (scaledHeight - dividedHeight) * ROOT2
        val xOffset = width / 2
        for (i in 0 until acs.samples.sizeHint(0)) {
            val sample = acs.samples[1].data[i]
            val x = sample.toDouble()
            val y = acs.samples[2].data[i].toDouble()
            val p = rotate45(x, y, dividedWidth, scaledHeight, xOffset, yOffset)
            if (i == 0) {
                gc.moveTo(p.x, p.y)
            } else {
                gc.lineTo(p.x, p.y)
            }
        }
        gc.closePath()
        gc.stroke()

        gc.fill = Color.VIOLET
        for (edge in edgePoints) {
            val p = rotate45(edge.first, edge.second, dividedWidth, scaledHeight, xOffset, yOffset)
            gc.fillRect(p.x - 1.0, p.y - 1.0, 2.0, 2.0)
        }
    }

    override fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(renderMode, "renderMode", VectorOrientation::class.java, this.javaClass, id)
        preferenceService.registerProperty(rangeX, "rangeX", this.javaClass, id)
        preferenceService.registerProperty(rangeY, "rangeY", this.javaClass, id)
        preferenceService.registerProperty(rangeLink, "rangeLink", this.javaClass, id)
        registerGraphPreferences(id, preferenceService)
    }

    override fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/vector")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox =
            loader.load(javaClass.classLoader.getResourceAsStream("layout/vector/vectorSettings.fxml"))
        val controller: VectorSettingsController = loader.getController()
        controller.vectorChartSettingsController.initialize(this)
        controlPane.children.add(controls)
    }

    override fun getCssMetaData(): List<CssMetaData<out Styleable?, *>> {
        return FACTORY.cssMetaData
    }

    enum class VectorOrientation {
        STRAIGTH,
        SKEWED
    }
}