package me.ksanstone.wavesync.wavesync.event

import javafx.stage.Stage
import org.springframework.context.ApplicationEvent

class StageReadyEvent(val stage: Stage) : ApplicationEvent(stage)