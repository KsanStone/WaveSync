package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Modality
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.WaveSyncApplication
import org.springframework.stereotype.Component

@Component
class MenuInitializer {
    fun showPopupMenu(fxml: String) {
        val dialog = Stage()
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.initOwner(WaveSyncApplication.primaryStage)
        val root: Parent = FXMLLoader.load(javaClass.classLoader.getResource(fxml))
        val dialogScene = Scene(root, root.prefWidth(-1.0), root.prefHeight(-1.0))
        dialog.setScene(dialogScene)
        dialog.show()
    }

}