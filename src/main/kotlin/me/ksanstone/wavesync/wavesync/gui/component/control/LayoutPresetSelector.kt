package me.ksanstone.wavesync.wavesync.gui.component.control

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.layout.HBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.LayoutPresetService
import java.util.*

class LayoutPresetSelector : HBox() {

    val menuButton = MenuButton()

    private val layoutPresetService =
        WaveSyncBootApplication.applicationContext.getBean(LayoutPresetService::class.java)

    init {
        alignment = Pos.CENTER
        children += menuButton

        update()
    }

    fun update() {
        menuButton.text = layoutPresetService.getCurrent()
        menuButton.items.clear()
        menuButton.items.add(MenuItem("new").apply {
            setOnAction {
                layoutPresetService.saveCurrentAsPreset(UUID.randomUUID().toString())
                update()
            }
        })
        layoutPresetService.getPresets().forEach {
            menuButton.items.add(
                CheckMenuItem(
                    it,
                    Button("kill").apply {
                        styleClass += "danger"; this.isDisable = it == layoutPresetService.getCurrent()
                    }).apply {
                    isSelected = it == layoutPresetService.getCurrent()
                    onAction = EventHandler { _event ->
                        layoutPresetService.loadPreset(it)
                        update()
                    }
                }
            )
        }
    }

}