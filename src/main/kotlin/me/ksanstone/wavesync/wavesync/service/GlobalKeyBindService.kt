package me.ksanstone.wavesync.wavesync.service

import javafx.event.EventType
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Stage
import org.springframework.stereotype.Service

@Service
class GlobalKeyBindService {

    val handlers = mutableListOf<Handler>()

    fun register(id: String, key: KeyCode, handler: (KeyEvent, Stage) -> Unit) {
        handlers.add(Handler(id, key, KeyEvent.KEY_PRESSED, handler))
    }

    fun register(id: String, key: KeyCode, type: EventType<KeyEvent>, handler: (KeyEvent, Stage) -> Unit) {
        handlers.add(Handler(id, key, type, handler))
    }

    fun registerForStage(stage: Stage) {
        listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED, KeyEvent.KEY_TYPED).forEach { type ->
            stage.addEventFilter(type) { event ->
                handlers.forEach {
                    if (it.type == type && it.code == event.code) {
                        it.handler.invoke(event, stage)
                    }
                }
            }
        }
    }

    data class Handler(
        val id: String,
        val code: KeyCode,
        val type: EventType<KeyEvent>,
        val handler: (KeyEvent, Stage) -> Unit
    )

}