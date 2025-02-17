package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Platform
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.window.CaptionConfiguration
import me.ksanstone.wavesync.wavesync.gui.window.CustomizedStage
import org.springframework.stereotype.Service

@Service
class StageManager {
    private val customizedStages = HashMap<Stage, CustomizedStage>()

    private fun isSupported(): Boolean {
        return Platform.isWindows()
    }

    fun registerStage(stage: Stage, config: CaptionConfiguration) {
        if (!isSupported()) return
        require(!customizedStages.containsKey(stage)) { "stage was already registered" }

        val customStage = CustomizedStage(stage, config)

        stage.showingProperty().addListener { _, _, newValue -> if (newValue) customStage.inject() }
        if (stage.isShowing) customStage.inject()

        customizedStages[stage] = customStage
    }

    fun releaseStage(stage: Stage) {
        val customizedStage = customizedStages[stage] ?: return
        customizedStage.release()
        customizedStages.remove(stage)
    }

}