package me.ksanstone.wavesync.wavesync.gui.component.control

import javafx.collections.MapChangeListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.layout.HBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.service.LayoutPresetService
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService

class LayoutPresetSelector : HBox() {

    private val menuButton = MenuButton()

    private val layoutPresetService =
        WaveSyncBootApplication.applicationContext.getBean(LayoutPresetService::class.java)
    private val layoutStorageService =
        WaveSyncBootApplication.applicationContext.getBean(LayoutStorageService::class.java)
    private val menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)

    init {
        alignment = Pos.CENTER
        children += menuButton
        layoutStorageService.storedLayouts.addListener(MapChangeListener { _ ->
            update()
        })
        update()
    }

    fun openNewDialog() {
        menuInitializer.showPopupMenu("layout/presetName.fxml", "New Preset")
    }

    fun update() {
        menuButton.text = "Load preset"
        menuButton.items.clear()
        menuButton.items.add(MenuItem("new").apply {
            setOnAction {
                openNewDialog()
            }
        })
        // skip the default layout
        layoutPresetService.getPresets().filter { it != "layout" }.forEach {
            menuButton.items.add(
                MenuItem(
                    it,
                    Button("kill").apply {
                        styleClass += "danger"; this.isDisable = it == layoutPresetService.getCurrent()
                        onAction = EventHandler { _ ->
                            layoutPresetService.deletePreset(it)
                        }
                    }
                ).apply {
                    onAction = EventHandler { _ ->
                        layoutPresetService.loadPreset(it)
                    }
                })
        }
    }

}