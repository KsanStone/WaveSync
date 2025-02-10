package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.scene.control.TextField
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.LayoutPresetService

class PresetName {

    @FXML
    lateinit var field: TextField

    private val layoutPresetService =
        WaveSyncBootApplication.applicationContext.getBean(LayoutPresetService::class.java)

    @FXML
    fun create() {
        if (field.text.isNotBlank() && field.text.length < 500) {
            layoutPresetService.saveCurrentAsPreset(field.text)
            field.scene.window.hide()
        }
    }

    @FXML
    fun cancel() {
        field.scene.window.hide()
    }

}