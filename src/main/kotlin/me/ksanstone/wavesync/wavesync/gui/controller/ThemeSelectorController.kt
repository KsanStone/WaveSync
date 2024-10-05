package me.ksanstone.wavesync.wavesync.gui.controller

import atlantafx.base.controls.ToggleSwitch
import atlantafx.base.theme.Theme
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
    lateinit var darkLightSwitch: ToggleSwitch

    @FXML
    lateinit var themeChoiceBox: ChoiceBox<String>

    private fun changeTheme() {
        if (currentPair == null) return
        themeService.applyTheme(if (darkLightSwitch.isSelected || currentPair!!.second == null) currentPair!!.first.name else currentPair!!.second!!.name)
    }

    private var currentPair: Pair<Theme, Theme?>? = null

    override fun initialize(p0: URL?, p1: ResourceBundle?) {
        try {
            this.themeService = WaveSyncBootApplication.applicationContext.getBean(ThemeService::class.java)
            themeChoiceBox.items.addAll(themeService.themePairs.map { it.first.name.replace("Dark", "") })
            themeChoiceBox.value = themeService.selectedTheme.value.replace("Dark", "")
            darkLightSwitch.isSelected = themeService.isDark.get()

            findPair(themeService.selectedTheme.value)

            darkLightSwitch.selectedProperty().addListener { _ ->
                changeTheme()
            }

            themeChoiceBox.valueProperty().addListener { _, _, v ->
                findPair(v)
                changeTheme()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun findPair(name: String) {
        currentPair =
            this.themeService.themePairs.find { it.first.name.contains(name) || it.second?.name?.contains(name) ?: false }!!
        darkLightSwitch.isSelected = if (currentPair!!.second == null) true else darkLightSwitch.isSelected
        darkLightSwitch.disableProperty().value = currentPair!!.second == null
    }
}