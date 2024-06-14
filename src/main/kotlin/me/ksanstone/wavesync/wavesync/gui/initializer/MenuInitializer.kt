package me.ksanstone.wavesync.wavesync.gui.initializer

import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.AnchorPane
import javafx.stage.Modality
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.WaveSyncApplication
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import org.springframework.stereotype.Component

@Component
class MenuInitializer(
    private val localizationService: LocalizationService,
    private val waveSyncStageInitializer: WaveSyncStageInitializer
) {

    private var dialogWindows: MutableMap<String, Stage> = mutableMapOf()

    @Synchronized
    fun <T> showPopupMenuWithController(fxml: String, title: String = "Dialog"): T {
        val stage = createDialogStage(fxml, title)
        waveSyncStageInitializer.customize(stage.first)
        stage.first.show()
        return stage.second.getController()
    }

    @Synchronized
    fun showPopupMenu(fxml: String, title: String = "Dialog") {
        if (dialogWindows.containsKey(fxml)) {
            dialogWindows[fxml]!!.show()
        } else {
            val stage = createDialogStage(fxml, title).first
            dialogWindows[fxml] = stage
            waveSyncStageInitializer.customize(stage)
            stage.show()
        }
    }

    private fun createDialogStage(fxml: String, title: String = "Dialog"): Pair<Stage, FXMLLoader> {
        val dialog = createEmptyStage(title)
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.initOwner(WaveSyncApplication.primaryStage)
        val loader = FXMLLoader(javaClass.classLoader.getResource(fxml))
        loader.resources = localizationService.getDefault()
        val root: Parent = loader.load()
        val dialogScene = Scene(root, root.prefWidth(-1.0), root.prefHeight(-1.0))

        dialog.minWidth = root.prefWidth(-1.0)
        dialog.minHeight = root.prefHeight(-1.0)
        dialog.scene = dialogScene
        return dialog to loader
    }

    fun createEmptyStage(title: String? = null, node: Node? = null): Stage {
        val stage = Stage()

        if (node != null) {
            val root = AnchorPane()
            root.children.add(node)
            stage.scene = Scene(root)
        }

        stage.title = if (title != null) "WaveSync • $title" else "WaveSync"
        stage.icons.add(Image("icon.png"))
        return stage
    }
}