package me.ksanstone.wavesync.wavesync.gui.component.info

import javafx.fxml.FXMLLoader
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.FFTInfoController
import me.ksanstone.wavesync.wavesync.service.LocalizationService

class FFTInfo(compact: Boolean = false) : AnchorPane() {

    private var controller: FFTInfoController

    init {
        val loader = FXMLLoader()
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val content: Pane =
            loader.load(javaClass.classLoader.getResourceAsStream("layout/fftInfo.fxml"))
        controller = loader.getController()
        controller.compact(compact)

        setTopAnchor(content, 0.0)
        setBottomAnchor(content, 0.0)
        setLeftAnchor(content, 0.0)
        setRightAnchor(content, 0.0)
        children.add(content)

    }

}