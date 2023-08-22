package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Modality
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.WaveSyncApplication
import org.springframework.stereotype.Component

@Component
class MenuInitializer {
    fun showPopupMenu(fxml: String, title: String = "Dialog") {
        val dialog = Stage()
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.initOwner(WaveSyncApplication.primaryStage)
        val root: Parent = FXMLLoader.load(javaClass.classLoader.getResource(fxml))
        val dialogScene = Scene(root, root.prefWidth(-1.0), root.prefHeight(-1.0))
        dialogScene.stylesheets.add("styles/style.css")

        dialog.minWidth = root.prefWidth(-1.0)
        dialog.minHeight = root.prefHeight(-1.0)
        dialog.scene = dialogScene
        dialog.title = "WaveSync • $title"
        dialog.icons.add(Image("icon.png"))
        dialog.show()
    }

}