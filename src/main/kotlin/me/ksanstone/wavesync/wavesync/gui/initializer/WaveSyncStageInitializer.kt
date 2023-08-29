package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.ThemeService
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component


@Component
class WaveSyncStageInitializer(
    private val themeService: ThemeService,
    private val localizationService: LocalizationService
) : ApplicationListener<StageReadyEvent> {
    override fun onApplicationEvent(event: StageReadyEvent) {
        val stage = event.stage
        val root: Parent =
            FXMLLoader.load(
                javaClass.classLoader.getResource("layout/index.fxml"),
                localizationService.getDefault()
            )
        val scene = Scene(root)

        themeService.applyCurrent()

        stage.title = "WaveSync"
        stage.icons.add(Image("icon.png"))
        stage.scene = scene
        stage.show()
    }
}