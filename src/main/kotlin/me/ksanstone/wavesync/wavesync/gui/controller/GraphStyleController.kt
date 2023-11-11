package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ToggleButton
import java.net.URL
import java.util.*

class GraphStyleController : Initializable {

    @FXML
    lateinit var gridToggle: ToggleButton

    @FXML
    lateinit var xAxisToggle: ToggleButton

    @FXML
    lateinit var yAxisToggle: ToggleButton

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {}
}