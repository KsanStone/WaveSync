package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform

import atlantafx.base.controls.ToggleSwitch
import javafx.fxml.FXML
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer

class WaveformChartSettingsController {

    @FXML
    lateinit var autoAlignToggleSwitch: ToggleSwitch

    fun initialize(visualizer: WaveformVisualizer) {
        autoAlignToggleSwitch.isSelected = visualizer.enableAutoAlign.get()
        visualizer.enableAutoAlign.bind(autoAlignToggleSwitch.selectedProperty())
    }

}