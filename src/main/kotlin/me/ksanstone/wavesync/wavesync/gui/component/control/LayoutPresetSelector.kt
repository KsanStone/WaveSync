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
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import org.kordamp.ikonli.javafx.FontIcon

class LayoutPresetSelector : HBox() {

    private val menuButton = MenuButton()

    private val localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
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

    private fun openNewDialog() {
        menuInitializer.showPopupMenu("layout/presetName.fxml", localizationService.get("preset.new"))
    }

    private fun openLoadDialog() {
        menuInitializer.showPopupMenu("layout/presetImportExport.fxml", localizationService.get("preset.io"), reset = true)
    }

    fun update() {
        menuButton.text = "Load preset"
        menuButton.items.clear()
        menuButton.items.add(MenuItem(localizationService.get("preset.menu.new"), FontIcon("mdmz-plus")).apply {
            setOnAction {
                openNewDialog()
            }
        })
        menuButton.items.add(MenuItem(localizationService.get("preset.menu.io"), FontIcon("mdomz-menu_open")).apply {
            setOnAction {
                openLoadDialog()
            }
        })
        // skip the default layout
        layoutPresetService.getPresets().filter { it != "layout" }.forEach {
            menuButton.items.add(
                MenuItem(
                it, Button().apply {
                    graphic = FontIcon("mdoal-delete")
                    styleClass += "danger"; this.isDisable = it == layoutPresetService.getCurrent()
                    onAction = EventHandler { _ ->
                        layoutPresetService.deletePreset(it)
                    }
                }).apply {
                onAction = EventHandler { _ ->
                    layoutPresetService.loadPreset(it)
                }
            })
        }
    }

}