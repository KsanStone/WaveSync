package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.lang.ref.WeakReference

@Service
class RecordingModeService(
    val preferenceService: PreferenceService
) {

    val recordingMode: BooleanProperty = SimpleBooleanProperty(false)

    private var stageRef: WeakReference<Stage>? = null
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun initialize() {
        preferenceService.registerProperty(recordingMode, "recordingMode", this.javaClass)
        recordingMode.addListener { _ -> updateTitle() }
        updateTitle()
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

    @EventListener
    fun stageReady(e: StageReadyEvent) {
        stageRef = WeakReference(e.stage)
        e.stage.addEventHandler(KeyEvent.KEY_PRESSED) { keyEvent ->
            if (KeyCode.F6 == keyEvent.code) {
                recordingMode.value = !recordingMode.value
            }
        }
    }

}