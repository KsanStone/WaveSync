package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Slider
import java.net.URL
import java.util.*

class VisualizerOptionsController : Initializable {

    @FXML var dropRateSlider: Slider? = null
    @FXML var scalingSlider: Slider? = null
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        MainController.instance.visualizer!!.scaling.bind(scalingSlider!!.valueProperty())
        MainController.instance.visualizer!!.dropRate.bind(dropRateSlider!!.valueProperty())
    }


}