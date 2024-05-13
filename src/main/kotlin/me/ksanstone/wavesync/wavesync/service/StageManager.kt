package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.window.CaptionConfiguration
import me.ksanstone.wavesync.wavesync.gui.window.CustomizedStage
import org.springframework.stereotype.Service

@Service
class StageManager {
    private val customizedStages = HashMap<Stage, CustomizedStage>()

    fun isSupported(): Boolean {
        return Platform.isWindows()
    }

    fun registerStage(stage: Stage, config: CaptionConfiguration) {
        require(!customizedStages.containsKey(stage)) { "stage was already registered" }

        val customStage = CustomizedStage(stage, config)

        if (!stage.isShowing) {
            stage.showingProperty().addListener(object : ChangeListener<Boolean> {
                override fun changed(
                    observable: ObservableValue<out Boolean>,
                    oldValue: Boolean,
                    newValue: Boolean
                ) {
                    customStage.inject()
                    stage.showingProperty().removeListener(this)
                }
            })
        } else {
            customStage.inject()
        }
        customizedStages[stage] = customStage
    }

    fun releaseStage(stage: Stage) {
        val customizedStage = customizedStages[stage]
            ?: throw IllegalArgumentException("cannot remove customization if stage was not customized")
        customizedStage.release()
        customizedStages.remove(stage)
    }
}