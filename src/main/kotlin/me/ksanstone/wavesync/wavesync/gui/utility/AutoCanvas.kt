package me.ksanstone.wavesync.wavesync.gui.utility

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.INFO_SHOWN
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.AutoCanvasInfoPaneController
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.utility.FPSCounter

abstract class AutoCanvas : AnchorPane() {

    protected var canvas: Canvas = Canvas()

    val framerate: IntegerProperty = SimpleIntegerProperty(ApplicationSettingDefaults.REFRESH_RATE)
    val info: BooleanProperty = SimpleBooleanProperty(INFO_SHOWN)

    private var lastDraw = System.nanoTime()
    private val frameTime = SimpleDoubleProperty(0.0)
    private val fpsCounter = FPSCounter()

    init {
        heightProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> drawCall() }
        widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> drawCall() }

        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(heightProperty())
        setTopAnchor(canvas, 0.0)
        setLeftAnchor(canvas, 0.0)
        children.add(canvas)

        minWidth = 1.0
        minHeight = 1.0
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE

        initializeInfoPane()
        initializeDrawLoop()
    }

    private fun initializeDrawLoop() {
        val drawLoop = Timeline(
            KeyFrame(Duration.seconds(1.0 / framerate.get()), { drawCall() })
        )

        drawLoop.cycleCount = Timeline.INDEFINITE
        drawLoop.play()

        parentProperty().addListener { _, _, newValue ->
            if (newValue != null && drawLoop.status != Animation.Status.RUNNING)
                drawLoop.play()
            else if (newValue == null && drawLoop.status == Animation.Status.RUNNING)
                drawLoop.pause()
        }

        framerate.addListener { _ ->
            drawLoop.pause()
            drawLoop.keyFrames[0] = KeyFrame(Duration.seconds(1.0 / framerate.get()), { drawCall() })
            drawLoop.playFromStart()
        }
    }

    private fun initializeInfoPane() {
        try {
            val loader = FXMLLoader()
            loader.resources =
                WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
            val pane: Pane = loader.load(javaClass.classLoader.getResourceAsStream("layout/autoCanvasInfo.fxml"),)
            val controller: AutoCanvasInfoPaneController = loader.getController()

            controller.targetFpsLabel.textProperty().bind(framerate.asString())
            controller.frameTimeLabel.textProperty().bind(frameTime.map { Duration.seconds(it.toDouble()).toString() })
            controller.fpsLabel.textProperty().bind(fpsCounter.current.asString("%.2f"))

            pane.visibleProperty().bind(info)

            setTopAnchor(pane, 5.0)
            setRightAnchor(pane, 5.0)

            children.add(pane)
        } catch(e: Exception) {e.printStackTrace()}
    }

    private fun drawCall() {
        val now = System.nanoTime()
        val deltaT = (now - lastDraw).toDouble() / 1_000_000_000.0
        lastDraw = now

        frameTime.set(deltaT)
        fpsCounter.tick()
        this.draw(canvas.graphicsContext2D, deltaT, now, canvas.width, canvas.height)
    }

    protected abstract fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double)

}