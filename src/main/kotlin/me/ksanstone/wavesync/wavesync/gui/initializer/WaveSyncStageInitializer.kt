package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component


@Component
class WaveSyncStageInitializer : ApplicationListener<StageReadyEvent> {
    override fun onApplicationEvent(event: StageReadyEvent) {
        val stage = event.stage
        val root: Parent = FXMLLoader.load(javaClass.classLoader.getResource("layout/index.fxml"))
        val scene = Scene(root)
        scene.stylesheets.add("styles/style.css")

        stage.title = "WaveSync"
        stage.icons.add(Image("icon.png"))
        stage.scene = scene
        stage.show()
    }
}