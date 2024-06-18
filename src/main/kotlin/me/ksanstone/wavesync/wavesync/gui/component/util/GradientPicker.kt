package me.ksanstone.wavesync.wavesync.gui.component.util

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.GradientEditorController
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.GradientSerializer
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SStartEndGradient
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import org.kordamp.ikonli.javafx.FontIcon

class GradientPicker : HBox() {

    val sGradient: ObjectProperty<SGradient?> = SimpleObjectProperty(SStartEndGradient(Color.RED, Color.BLUE))

    private val openButton = Button()
    private val previewRect = Rectangle()

    private val menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
    private val gradientSerializer = WaveSyncBootApplication.applicationContext.getBean(GradientSerializer::class.java)

    init {
        sGradient.addListener { _ -> invalidateGradient() }
        invalidateGradient()

        openButton.setOnAction { openDialog() }
        openButton.graphic = FontIcon("mdal-edit")

        previewRect.width = 150.0
        previewRect.height = 32.0

        isFillHeight = true
        spacing = 5.0
        children.addAll(previewRect, openButton)
    }

    private fun openDialog() {
        val stageController: Pair<Stage, GradientEditorController> =
            menuInitializer.showPopupMenuWithController("layout/gradientEditor.fxml", title = "Gradient")

        stageController.second.gradient.value = sGradient.value
        stageController.second.readyGradient.addListener { _, _, v ->
            stageController.first.close()
            sGradient.value = v
        }
        stageController.second.shouldClose.addListener { _, _, v ->
            if(v) stageController.first.close()
        }
    }

    private fun invalidateGradient() {
        colorRect()
    }

    private fun colorRect() {
        val stops = sGradient.value?.let {
            gradientSerializer.toStops(it)
        } ?: listOf(Stop(0.0, Color.TRANSPARENT), Stop(1.0, Color.TRANSPARENT))

        previewRect.fill = LinearGradient(0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE, stops)
    }
}