package me.ksanstone.wavesync.wavesync.gui.initializer

import jakarta.annotation.PostConstruct
import javafx.beans.value.ChangeListener
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.WaveSyncApplication
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import me.ksanstone.wavesync.wavesync.gui.component.control.MainControl
import me.ksanstone.wavesync.wavesync.gui.window.CaptionConfiguration
import me.ksanstone.wavesync.wavesync.service.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import java.util.function.Consumer
import kotlin.time.measureTime


@Component
class WaveSyncStageInitializer(
    private val themeService: ThemeService,
    private val localizationService: LocalizationService,
    private val stageSizingService: StageSizingService,
    private val audioCaptureService: AudioCaptureService,
    private val stageManager: StageManager,
    private val layoutStorageService: LayoutStorageService,
    private val globalKeyBindService: GlobalKeyBindService
) : ApplicationListener<StageReadyEvent> {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun init() {
        globalKeyBindService.register("fullscreen", KeyCode.F11) { _, stage ->
            stage.isFullScreen = !stage.isFullScreen
        }
        globalKeyBindService.register("togglePause", KeyCode.K) { _, _ ->
            audioCaptureService.paused.value = !audioCaptureService.paused.value
        }
        globalKeyBindService.register("holdPause", KeyCode.L) { _, _ ->
            audioCaptureService.paused.value = true
        }
        globalKeyBindService.register("holdPause", KeyCode.L, KeyEvent.KEY_RELEASED) { _, _ ->
            audioCaptureService.paused.value = false
        }
        globalKeyBindService.register("justify", KeyCode.J) { _, stage ->
            layoutStorageService.getLayout(stage)?.justify()
        }
    }

    override fun onApplicationEvent(event: StageReadyEvent) {
        val stageInitTime = measureTime {
            val stage = event.stage
            registerAccelerators(stage)

            val root: Parent =
                FXMLLoader.load(
                    javaClass.classLoader.getResource("layout/index.fxml"),
                    localizationService.getDefault()
                )
            val scene = Scene(root)

            themeService.applyCurrent()
            stageSizingService.registerStageSize(stage, "main")

            stage.title = "WaveSync"
            stage.minWidth = 500.0
            stage.minHeight = 350.0
            stage.icons.add(Image("icon.png"))
            stage.scene = scene
            customize(stage)

            stage.show()
            WaveSyncApplication.logTimePoint("Showing stage")
        }
        logger.info("Stage init took $stageInitTime")
    }

    /**
     * Prepare a stage for general usage
     *
     * @param id used to remember the stage's size
     * @param autoDispose if true, the stage will be automatically deregistered
     * from the stage sizing service upon closing
     *
     * @return The stage
     */
    fun createGeneralPurposeAppFrame(
        id: String,
        autoDispose: AutoDisposalMode = AutoDisposalMode.NONE,
        skipRegister: Boolean = false,
        autoDisposalListener: Consumer<Unit> = Consumer {},
    ): Stage {
        val stage = Stage()
        registerAccelerators(stage)

        stage.icons.add(Image("icon.png"))
        stage.title = "WaveSync"
        stage.minWidth = 500.0
        stage.minHeight = 350.0

        if (!skipRegister)
            stageSizingService.registerStageSize(stage, id)

        if (autoDispose == AutoDisposalMode.USER) {
            stage.setOnCloseRequest {
                stageSizingService.unregisterStage(id)
                autoDisposalListener.accept(Unit)
            }
        } else if (autoDispose == AutoDisposalMode.ALL) {
            stage.showingProperty().addListener { _, _, v ->
                if (!v) {
                    stageSizingService.unregisterStage(id)
                    autoDisposalListener.accept(Unit)
                }
            }
        }

        customize(stage)

        return stage
    }

    private fun registerAccelerators(stage: Stage) {
        stage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
        globalKeyBindService.registerForStage(stage)
    }

    /**
     * Register custom controls and take care of the scene
     */
    fun customize(stage: Stage) {
        if (stage.scene == null) {
            var listener: ChangeListener<Scene>? = null
            listener = ChangeListener<Scene> { _, _, v ->
                if (v != null)
                    injectControls(stage)
                stage.sceneProperty().removeListener(listener)
            }
            stage.sceneProperty().addListener(listener)
        } else {
            injectControls(stage)
        }
    }

    private fun injectControls(stage: Stage) {
        val parent = stage.scene.root
        val control = MainControl().apply { this.children.add(parent); VBox.setVgrow(parent, Priority.ALWAYS) }
        stage.scene.root = control
        stageManager.registerStage(stage, CaptionConfiguration())
    }
}

enum class AutoDisposalMode {
    /**
     * Noop
     */
    NONE,

    /**
     * Will not dispose upon app close
     */
    USER,

    /**
     * Will dispose upon app close
     */
    ALL
}