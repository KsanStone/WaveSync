package me.ksanstone.wavesync.wavesync.event

import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.controller.MainController
import org.springframework.context.ApplicationEvent

class FXMLInitializeEvent(private val controller: MainController) : ApplicationEvent(controller)