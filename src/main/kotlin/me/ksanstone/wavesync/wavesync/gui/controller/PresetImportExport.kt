package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.utility.ResettableForm
import me.ksanstone.wavesync.wavesync.service.LayoutPresetService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import java.net.URL
import java.util.*

class PresetImportExport : Initializable, ResettableForm {

    @FXML
    private lateinit var importErrorMessage: Label

    @FXML
    private lateinit var importedLayoutName: TextField

    @FXML
    private lateinit var layoutInputField: TextArea

    @FXML
    private lateinit var exportedLayoutField: TextArea

    @FXML
    private lateinit var exportChooser: ChoiceBox<String>


    private val layoutPresetService =
        WaveSyncBootApplication.applicationContext.getBean(LayoutPresetService::class.java)
    private val localizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)

    @FXML
    fun create() {
        if (importedLayoutName.text.isBlank()) {
            importErrorMessage.text = localizationService.get("preset.import.error.name")
            return
        }
        try {
            layoutPresetService.importPreset(importedLayoutName.text, layoutInputField.text)
        } catch (e: Exception) {
            importErrorMessage.text = localizationService.get("preset.import.error.layout")
            return
        }
        close()
    }

    @FXML
    fun cancel() {
        close()
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        exportChooser.items.setAll(layoutPresetService.getPresets().map { it })
        exportChooser.valueProperty().addListener { _, _, newValue ->
            exportedLayoutField.text = layoutPresetService.exportPreset(newValue ?: "") ?: ""
        }
    }

    private fun close() {
        importedLayoutName.scene.window.hide()
        reset()
    }

    override fun reset() {
        importErrorMessage.text = ""
        layoutInputField.text = ""
        exportedLayoutField.text = ""
        exportChooser.items.setAll(layoutPresetService.getPresets().map { it })
    }
}