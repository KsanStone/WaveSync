package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component


@Component
class WaveSyncStageInitializer(
    private val applicationContext: ConfigurableApplicationContext
) : ApplicationListener<StageReadyEvent> {
    override fun onApplicationEvent(event: StageReadyEvent) {
        val stage = event.stage
        val root: Parent = FXMLLoader.load(javaClass.classLoader.getResource("layout/index.fxml"))
        val scene = Scene(root)

        stage.title = "WaveSync"
        stage.scene = scene
        stage.show()
    }
}