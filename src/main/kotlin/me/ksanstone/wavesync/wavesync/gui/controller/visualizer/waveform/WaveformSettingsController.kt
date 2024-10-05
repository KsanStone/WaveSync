package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform

import javafx.fxml.FXML
import javafx.scene.control.Label

class WaveformSettingsController {
    @FXML
    lateinit var channelLabel: Label

    @FXML
    lateinit var waveformChartSettingsController: WaveformChartSettingsController
}