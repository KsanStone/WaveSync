package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import com.huskerdev.openglfx.canvas.GLCanvas
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
import me.ksanstone.wavesync.wavesync.gui.utility.GlUtil
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import org.lwjgl.opengl.ARBShaderImageLoadStore.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER
import org.lwjgl.opengl.GL43.glDispatchCompute
import java.nio.ByteBuffer
import kotlin.math.*

class VectorScopeVisualizer : AutoCanvas(true) {

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
    val decay: FloatProperty = SimpleFloatProperty(0.67f)

    private lateinit var lBuffer: RollingBuffer<Float>
    private lateinit var rBuffer: RollingBuffer<Float>
    private var lastWritten: Long = 0

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
            } else if (a >= Math.PI * 1.25) {
                nums.add(-cos * (1 / sin) to -1.0)
            }
        }

        edgePoints = nums

        sizeBuffers()
        acs.source.addListener { _ -> sizeBuffers() }
    }

    private fun sizeBuffers() {
        lBuffer = RollingBuffer(((acs.source.get()?.rate?.toDouble() ?: 100.0) * 0.05).roundToInt()) { 0.0f }
        rBuffer = RollingBuffer(((acs.source.get()?.rate?.toDouble() ?: 100.0) * 0.05).roundToInt()) { 0.0f }
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
        if (acs.samples.channels() != 2 + 1) { // combined + L + R
            return
        }

        val iterator = when (renderMode.value!!) {
            VectorOrientation.SKEWED -> pointsSkewed(width, height)
            VectorOrientation.STRAIGHT -> drawVertical(width, height)
        }.iterator()

        if (iterator.next() != null) {
            val first = iterator.next()
            gc.beginPath()
            gc.moveTo(first!!.x, first.y)
            for (point in iterator) {
                gc.lineTo(point!!.x, point.y)
            }
            gc.closePath()
            gc.stroke()
        }

    }

    data class GlData(
        val lightnessTexture: Int = 0,
        val lightnessFramebuffer: Int = 0,
        val colorTexture: Int = 0,
        val colorFramebuffer: Int = 0,
    ) {
        fun delete() {
            glDeleteFramebuffers(lightnessFramebuffer)
            glDeleteFramebuffers(colorFramebuffer)
            glDeleteTextures(colorTexture)
            glDeleteTextures(lightnessTexture)
        }
    }

    private fun createImageBuffers(width: Int, height: Int): GlData {
        val lightnessTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, lightnessTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, height, 0, GL_RED, GL_FLOAT, null as ByteBuffer?)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val framebuffer = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightnessTexture, 0)

        val rbo = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, rbo)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
        glBindRenderbuffer(GL_RENDERBUFFER, 0)

        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rbo)

        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        val colorTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, colorTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, null as ByteBuffer?)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val colorFramebuffer = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, colorFramebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0)

        return GlData(lightnessTexture, framebuffer, colorTexture, colorFramebuffer)
    }

    override fun setupGl(canvas: GLCanvas) {
        var glData = GlData()
        var dimProgram = 0
        var colorProgram = 0


        canvas.addOnInitEvent { _ ->
            glData = createImageBuffers(800, 800)

            val dimShader = GlUtil.compileShader("/shaders/dim.compute.glsl", GL_COMPUTE_SHADER)
            dimProgram = GlUtil.linkProgram(listOf(dimShader))

            val colorShader = GlUtil.compileShader("/shaders/color.compute.glsl", GL_COMPUTE_SHADER)
            colorProgram = GlUtil.linkProgram(listOf(colorShader))
        }

        canvas.addOnRenderEvent { event ->
            if (acs.samples.channels() != 2 + 1) { // combined + L + R
                return@addOnRenderEvent
            }

            // Camera
            glMatrixMode(GL_PROJECTION)
            glLoadIdentity()
            glOrtho(0.0, event.width.toDouble(), event.height.toDouble(), 0.0, -1.0, 1.0)
            glMatrixMode(GL_MODELVIEW)

            // Point generator
            val iterator = when (renderMode.value!!) {
                VectorOrientation.SKEWED -> pointsSkewed(event.width.toDouble(), event.height.toDouble())
                VectorOrientation.STRAIGHT -> drawVertical(event.width.toDouble(), event.height.toDouble())
            }.iterator()
            if (iterator.next() == null)
                return@addOnRenderEvent

            // Clear main framebuffer and bing out custom one
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            glBindFramebuffer(GL_FRAMEBUFFER, glData.lightnessFramebuffer)

            // Draw the lines
            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE) // Additive blending
            glBegin(GL_LINES)
            var pointA = iterator.next()!!
            for (pointB in iterator) {
                var len = pointA.distance(pointB!!)
                if (len.isNaN()) len = 0.25

                glColor4f((1 / len).toFloat(), 0.0f, 0.0f, 1.0f)

                glVertex2d(pointA.x, pointA.y)
                glVertex2d(pointB.x, pointB.y)
                pointA = pointB
            }
            glEnd()

            // Apply dimming
            glUseProgram(dimProgram)
            val lambda = -ln(decay.value) * 60
            val adjustedDecayFactor = exp(-lambda * event.delta)
            glUniform1f(1, adjustedDecayFactor.toFloat())
            glBindImageTexture(0, glData.lightnessTexture, 0, false, 0, GL_READ_WRITE, GL_R32F)

            glDispatchCompute(event.width, event.height, 1)
            glMemoryBarrier(GL_ALL_BARRIER_BITS)

            // Color the output
            val color = vectorColor.value
            glUseProgram(colorProgram)
            glUniform3f(1, color.red.toFloat(), color.green.toFloat(), color.blue.toFloat())

            glBindImageTexture(0, glData.lightnessTexture, 0, false, 0, GL_READ_WRITE, GL_R32F)
            glBindImageTexture(1, glData.colorTexture, 0, false, 0, GL_READ_WRITE, GL_RGBA32F)

            glDispatchCompute(event.width, event.height, 1)
            glMemoryBarrier(GL_ALL_BARRIER_BITS)

            glColorMask(true, true, true, true);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, glData.colorFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, event.fbo)
            glBlitFramebuffer(
                0,
                0,
                event.width,
                event.height,
                0,
                0,
                event.width,
                event.height,
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST
            )
        }

        canvas.addOnReshapeEvent { event ->
            glData.delete()
            glData = createImageBuffers(event.width, event.height)
        }

        canvas.addOnDisposeEvent { _ ->
            glData.delete()
        }
    }


    private fun pointsSkewed(width: Double, height: Double) = sequence {
        val sX = 1.0 / rangeX.value
        val sY = 1.0 / rangeY.value
        val written = (lBuffer.written - lastWritten).coerceAtMost(lBuffer.size.toLong())
        lastWritten = lBuffer.written
        if (written == 0L)
            yield(null)
        else
            yield(Point2D(0.0, 0.0))

        for (i in lBuffer.size - written until lBuffer.size) {
            val lSample = lBuffer[i.toInt()]
            val rSample = rBuffer[i.toInt()]

            yield(
                Point2D(
                    (lSample.toDouble() * sX + 1.0) * 0.5 * width - 1.0,
                    height - (rSample.toDouble() * sY + 1) * height * 0.5
                )
            )
        }
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


    private fun drawVertical(width: Double, height: Double) = sequence {
        val sX = 1.0 / rangeX.value
        val sY = 1.0 / rangeY.value
        val dividedWidth = (width - 4) / 2 / ROOT2 * sX
        val dividedHeight = height / 2 / ROOT2
        val scaledHeight = dividedHeight * sY
        val yOffset = (ROOT2 - 1) * scaledHeight - (scaledHeight - dividedHeight) * ROOT2
        val xOffset = width / 2
        val written = (lBuffer.written - lastWritten).coerceAtMost(lBuffer.size.toLong())
        lastWritten = lBuffer.written

        if (written == 0L)
            yield(null)
        else
            yield(Point2D(0.0, 0.0))

        for (i in lBuffer.size - written until lBuffer.size) {
            val lSample = lBuffer[i.toInt()].toDouble()
            val rSample = rBuffer[i.toInt()].toDouble()
            yield(rotate45(lSample, rSample, dividedWidth, scaledHeight, xOffset, yOffset))
        }
        /*
        gc.fill = Color.VIOLET
        for (edge in edgePoints) {
            val p = rotate45(edge.first, edge.second, dividedWidth, scaledHeight, xOffset, yOffset)
            gc.fillRect(p.x - 1.0, p.y - 1.0, 2.0, 2.0)
        }
        */
    }

    override fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(renderMode, "renderMode", VectorOrientation::class.java, this.javaClass, id)
        preferenceService.registerProperty(rangeX, "rangeX", this.javaClass, id)
        preferenceService.registerProperty(rangeY, "rangeY", this.javaClass, id)
        preferenceService.registerProperty(decay, "decay", this.javaClass, id)
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

    private fun handleSamples(sampleEvent: AudioCaptureService.SampleEvent) {
        if (!canDraw) return
        when (sampleEvent.channel) {
            1 -> lBuffer
            2 -> rBuffer
            else -> null
        }?.insert(sampleEvent.data.toTypedArray())
    }

    override fun registerListeners(acs: AudioCaptureService) {
        acs.registerSampleObserver(1, this::handleSamples)
        acs.registerSampleObserver(2, this::handleSamples)
    }

    enum class VectorOrientation {
        STRAIGHT,
        SKEWED
    }
}