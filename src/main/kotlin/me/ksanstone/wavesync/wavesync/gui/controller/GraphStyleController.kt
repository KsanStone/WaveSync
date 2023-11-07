package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ToggleButton
import org.kordamp.ikonli.javafx.FontIcon
import java.net.URL
import java.util.*

class GraphStyleController : Initializable{

    @FXML
    lateinit var gridToggleIcon: FontIcon

    @FXML
    lateinit var gridToggle: ToggleButton

    @FXML
    lateinit var xAxisToggle: ToggleButton

    @FXML
    lateinit var yAxisToggle: ToggleButton

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        gridToggle.isDisable = true
        gridToggle.selectedProperty().addListener { _ -> updateIcon() }
        updateIcon()
    }

    private fun updateIcon() {
        gridToggleIcon.iconLiteral = if (gridToggle.isSelected) "mdal-grid_off" else "mdal-grid_on"
    }

}