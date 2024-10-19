package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.layout.GridPane
import javafx.scene.layout.RowConstraints
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.util.Keybinding
import me.ksanstone.wavesync.wavesync.service.GlobalKeyBindService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import java.net.URL
import java.util.*

class KeybindController : Initializable {

    @FXML
    lateinit var keybindPane: GridPane

    private val globalKeyBindService: GlobalKeyBindService =
        WaveSyncBootApplication.applicationContext.getBean(GlobalKeyBindService::class.java)
    private val localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        for (bind in globalKeyBindService.handlers.filter { it.type == KeyEvent.KEY_PRESSED }) {
            keybindPane.rowConstraints.add(RowConstraints().also { it.minHeight = 50.0 })
            keybindPane.add(
                Label(localizationService.get("keybind." + bind.id)),
                0,
                keybindPane.rowConstraints.size - 1
            )
            keybindPane.add(
                Keybinding().also { it.keyCodes.add(bind.code) },
                1,
                keybindPane.rowConstraints.size - 1
            )
        }
    }

}