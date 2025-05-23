package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import me.ksanstone.wavesync.wavesync.gui.controller.MainController
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LayoutPresetService(
    val storageService: LayoutStorageService,
    val globalLayoutService: GlobalLayoutService,
    val layoutSerializerService: DragLayoutSerializerService,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun deletePreset(name: String) {
        if (storageService.storedLayouts.remove(name) != null) logger.info("Removed layout preset: $name")
    }

    fun saveCurrentAsPreset(name: String) {
        storageService.save(name)
        logger.info("Saved current layout as preset: $name")
    }

    /**
     * Export the preset in JSON format.
     */
    fun exportPreset(name: String): String? {
        return storageService.storedLayouts[name]
    }

    /**
     * Import a preset JSON, replacing an existing layout if it exists.
     * @throws IllegalArgumentException if the content and/or name is invalid
     * @throws NullPointerException if any leaf contains a component of an unknown, id
     */
    fun importPreset(name: String, content: String) {
        layoutSerializerService.deserializeFull(content, storageService.nodeFactory)
        storageService.storedLayouts[name] = content
    }

    fun loadPreset(name: String) {
        if (!getPresets().contains(name)) {
            logger.debug("Skipping loading non-extant layout preset: $name")
            return
        }
        logger.info("Loading layout preset: $name")
        globalLayoutService.loadLayouts(name)

        MainController.instance.replaceLayout(globalLayoutService.mainLayout)
    }

    fun getPresets(): List<String> {
        return storageService.storedLayouts.keys.toList()
    }

    fun getCurrent(): String {
        return storageService.activeLayoutSetId
    }

    @PostConstruct
    fun init() {
        logger.debug("Loaded ${storageService.storedLayouts.size} presets:")
        for ((k, v) in storageService.storedLayouts) {
            logger.debug("Loaded $k preset: $v")
        }
    }
}