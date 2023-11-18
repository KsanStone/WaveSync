package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.StageSizingService
import me.ksanstone.wavesync.wavesync.service.ThemeService
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component


@Component
class WaveSyncStageInitializer(
    private val themeService: ThemeService,
    private val localizationService: LocalizationService,
    private val stageSizingService: StageSizingService
) : ApplicationListener<StageReadyEvent> {

    override fun onApplicationEvent(event: StageReadyEvent) {
        val stage = event.stage

        stage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
        stage.addEventHandler(KeyEvent.KEY_PRESSED) { keyEvent ->
            if (KeyCode.F11 == keyEvent.code) {
                stage.isFullScreen = !stage.isFullScreen
            }
        }

        val root: Parent =
            FXMLLoader.load(
                javaClass.classLoader.getResource("layout/index.fxml"),
                localizationService.getDefault()
            )
        val scene = Scene(root)

        themeService.applyCurrent()

        stageSizingService.registerStageSize(stage, "main")

        stage.title = "WaveSync"
        stage.icons.add(Image("icon.png"))
        stage.scene = scene
        stage.show()
    }
}