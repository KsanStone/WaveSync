package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Modality
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.WaveSyncApplication
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import org.springframework.stereotype.Component

@Component
class MenuInitializer(
    private val localizationService: LocalizationService
) {

    private var dialogWindows: MutableMap<String, Stage> = mutableMapOf()

    @Synchronized
    fun showPopupMenu(fxml: String, title: String = "Dialog") {
        if (dialogWindows.containsKey(fxml)) {
            dialogWindows[fxml]!!.show()
        } else {
            val stage = createDialogStage(fxml, title)
            dialogWindows[fxml] = stage
            stage.show()
        }
    }

    fun createDialogStage(fxml: String, title: String = "Dialog"): Stage {
        val dialog = Stage()
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.initOwner(WaveSyncApplication.primaryStage)
        val root: Parent = FXMLLoader.load(javaClass.classLoader.getResource(fxml), localizationService.getDefault())
        val dialogScene = Scene(root, root.prefWidth(-1.0), root.prefHeight(-1.0))

        dialog.minWidth = root.prefWidth(-1.0)
        dialog.minHeight = root.prefHeight(-1.0)
        dialog.scene = dialogScene
        dialog.title = "WaveSync • $title"
        dialog.icons.add(Image("icon.png"))
        return dialog
    }

}