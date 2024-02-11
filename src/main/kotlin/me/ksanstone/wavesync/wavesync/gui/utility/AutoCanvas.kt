package me.ksanstone.wavesync.wavesync.gui.utility

import javafx.animation.Animation
import javafx.animation.FadeTransition
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.effect.BlurType
import javafx.scene.effect.DropShadow
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.INFO_SHOWN
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.AutoCanvasInfoPaneController
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.RecordingModeService
import me.ksanstone.wavesync.wavesync.utility.FPSCounter
import org.kordamp.ikonli.javafx.FontIcon
import org.springframework.beans.factory.getBean
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.roundToLong


abstract class AutoCanvas(private val detachable: Boolean = true) : AnchorPane() {

    protected var canvas: Canvas = Canvas()
    protected lateinit var infoPane: GridPane
    protected lateinit var controlPane: HBox
    protected val detachedWindowNameProperty: StringProperty = SimpleStringProperty("AutoCanvas")

    protected var xAxis = NumberAxis(0.0, 100.0, 10.0)
    protected var yAxis = NumberAxis(0.0, 100.0, 10.0)

    var canvasContainer: GraphCanvas
    val framerate: IntegerProperty = SimpleIntegerProperty(ApplicationSettingDefaults.REFRESH_RATE)
    val info: BooleanProperty = SimpleBooleanProperty(INFO_SHOWN)

    private var detachedStage: Stage? = null
    private var lastDraw = System.nanoTime()
    private val frameTime = SimpleDoubleProperty(0.0)
    private val fpsCounter = FPSCounter()
    private val detachedProperty = SimpleBooleanProperty(false)
    private var recordingModeService: RecordingModeService = WaveSyncBootApplication.applicationContext.getBean(RecordingModeService::class)
    private lateinit var drawLoop: Timeline

    internal val isPaused: Boolean
        get() = drawLoop.status !== Animation.Status.RUNNING

    init {
        heightProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> drawCall() }
        widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> drawCall() }

        canvasContainer = GraphCanvas(xAxis, yAxis, canvas)
        setBottomAnchor(canvasContainer, 0.0)
        setRightAnchor(canvasContainer, 0.0)
        setTopAnchor(canvasContainer, 0.0)
        setLeftAnchor(canvasContainer, 0.0)
        children.add(canvasContainer)

        minWidth = 1.0
        minHeight = 1.0
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE

        initializeInfoPane()
        initializeControlPane()
        initializeDrawLoop()
        initializeDetacherProperty()
    }

    private fun initializeDrawLoop() {
        drawLoop = Timeline(
            KeyFrame(Duration.seconds(1.0 / framerate.get()), { drawCall() })
        )

        drawLoop.cycleCount = Timeline.INDEFINITE
        drawLoop.play()

        parentProperty().addListener { _, _, newValue ->
            if (newValue != null && drawLoop.status != Animation.Status.RUNNING) {
                drawLoop.play()
            } else if (newValue == null && drawLoop.status == Animation.Status.RUNNING) {
                drawLoop.pause()
            }
        }

        framerate.addListener { _ ->
            drawLoop.pause()
            drawLoop.keyFrames[0] = KeyFrame(Duration.seconds(1.0 / framerate.get()), { drawCall() })
            drawLoop.playFromStart()
        }
    }

    private fun initializeDetacherProperty() {
        detachedProperty.addListener { _, _, v ->
            if (v) {
                if (detachedStage == null) {
                    detachedStage = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
                        .createEmptyStage("", Label())
                    detachedStage!!.titleProperty().bind(detachedWindowNameProperty.map { if (it.isNotEmpty()) "WaveSync • $it" else "WaveSync" })
                    detachedStage!!.showingProperty()
                        .addListener { _, _, showing -> if (!showing) detachedProperty.set(false) }
                }

                (detachedStage!!.scene.root as AnchorPane).children.add(canvasContainer)
                (detachedStage!!.scene.root as AnchorPane).children.add(infoPane)
                (detachedStage!!.scene.root as AnchorPane).children.add(controlPane)
                children.remove(canvasContainer)
                children.remove(infoPane)
                children.remove(controlPane)
                detachedStage!!.show()
            } else {
                if (detachedStage == null) return@addListener
                detachedStage!!.hide()
                children.add(canvasContainer)
                children.add(infoPane)
                children.add(controlPane)
                (detachedStage!!.scene.root as AnchorPane).children.remove(canvasContainer)
                (detachedStage!!.scene.root as AnchorPane).children.remove(infoPane)
                (detachedStage!!.scene.root as AnchorPane).children.remove(controlPane)
            }
        }
    }

    private fun initializeControlPane() {
        controlPane = HBox()
        controlPane.visibleProperty().bind(recordingModeService.recordingMode.not())
        controlPane.styleClass.add("control-box")
        controlPane.stylesheets.add("/styles/canvas-control.css")
        controlPane.hoverProperty().addListener { _, _, v ->
            val ft = FadeTransition(Duration.millis(100.0), controlPane)
            if (v) {
                ft.fromValue = 0.2
                ft.toValue = 1.0
            } else {
                ft.fromValue = 1.0
                ft.toValue = 0.2
            }
            ft.cycleCount = 1
            ft.play()
        }
        controlPane.opacity = 0.2

        setTopAnchor(controlPane, 5.0)
        setRightAnchor(controlPane, 5.0)

        if(detachable) {
            val detachButton = Button()
            detachButton.styleClass.add("button-icon")
            detachButton.graphic = FontIcon(if (detachedProperty.get()) "mdmz-south_west" else "mdmz-open_in_new")
            detachedProperty.addListener { _, _, v ->
                (detachButton.graphic as FontIcon).iconLiteral = if (v) "mdmz-south_west" else "mdmz-open_in_new"
            }
            detachButton.onAction = EventHandler { detachedProperty.set(!detachedProperty.get()) }
            controlPane.children.add(detachButton)
        }

        children.add(controlPane)
    }

    private fun initializeInfoPane() {
        val loader = FXMLLoader()
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        infoPane = loader.load(javaClass.classLoader.getResourceAsStream("layout/autoCanvasInfo.fxml"))
        val controller: AutoCanvasInfoPaneController = loader.getController()

        controller.targetFpsLabel.textProperty().bind(framerate.asString())
        controller.frameTimeLabel.textProperty().bind(frameTime.map {
            "${
                Duration.millis(
                    fpsCounter.averagedFrameTimeProperty.value.times(1000).roundTo(3)
                )
            }"
        })
        controller.realTimeFrameTimeLabel.textProperty()
            .bind(frameTime.map { "${Duration.millis(it.toDouble().times(1000))}" })
        controller.fpsLabel.textProperty().bind(fpsCounter.current.asString("%.2f"))

        infoPane.visibleProperty().bind(info)
        val dropShadow = DropShadow()
        dropShadow.offsetX = 2.0
        dropShadow.offsetY = 2.0
        dropShadow.blurType = BlurType.GAUSSIAN
        dropShadow.radius = 5.0
        infoPane.effect = dropShadow

        setTopAnchor(infoPane, 5.0)
        setLeftAnchor(infoPane, 5.0)

        children.add(infoPane)
    }

    private val isDrawing = AtomicBoolean(false)

    private fun drawCall() {
        if (isDrawing.get()) return

        val now = System.nanoTime()
        val deltaT = (now - lastDraw).toDouble() / 1_000_000_000.0
        lastDraw = now

        frameTime.set(deltaT)
        isDrawing.set(true)
        this.draw(canvas.graphicsContext2D, deltaT, now, canvas.width, canvas.height)
        isDrawing.set(false)
        fpsCounter.tick(deltaT)
    }

    protected abstract fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double)
}

fun Double.roundTo(precision: Int): Double {
    val f = 10.0.pow(precision)
    return (this * f).roundToLong() / f
}