package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ChoiceBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.ThemeService
import java.net.URL
import java.util.*

class ThemeSelectorController : Initializable {

    private lateinit var themeService: ThemeService

    @FXML
    lateinit var themeChoiceBox: ChoiceBox<String>

    @FXML
    fun changeTheme() {
        themeService.applyTheme(themeChoiceBox.value)
    }

    override fun initialize(p0: URL?, p1: ResourceBundle?) {
        try {
            this.themeService = WaveSyncBootApplication.applicationContext.getBean(ThemeService::class.java)
            themeChoiceBox.items.addAll(themeService.themes.keys)
            themeChoiceBox.valueProperty().bindBidirectional(themeService.selectedTheme)
        } catch (e: Exception) {
            System.err.println("Apparently we are running in scene builder")
        }
    }

}