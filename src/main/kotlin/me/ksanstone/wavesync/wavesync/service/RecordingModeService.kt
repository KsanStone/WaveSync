package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.input.KeyCode
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.ref.WeakReference

@Service
class RecordingModeService(
    val preferenceService: PreferenceService,
    private val globalKeyBindService: GlobalKeyBindService
) {

    val recordingMode: BooleanProperty = SimpleBooleanProperty(false)

    private var stageRef: WeakReference<Stage>? = null
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun initialize() {
        preferenceService.registerProperty(recordingMode, "recordingMode", this.javaClass)
        recordingMode.addListener { _ -> updateTitle() }
        updateTitle()
        globalKeyBindService.register("recMode", KeyCode.F6) { _, _ ->
            recordingMode.value = !recordingMode.value
        }
    }

    private fun updateTitle() {
        val titleSuffix = " • Clean mode - F6"
        val stage = stageRef?.get() ?: return
        if (recordingMode.value) {
            stage.title += titleSuffix
        } else {
            stage.title = stage.title.replace(titleSuffix, "")
        }
        logger.info("Clean mode ${recordingMode.value}")
    }

}