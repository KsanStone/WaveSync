package me.ksanstone.wavesync.wavesync.service

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.math.roundToInt


@Service
class StageSizingService(
    private val preferenceService: PreferenceService
) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    fun registerStageSize(stage: Stage, id: String) {
        val widthPropWrapper = SimpleDoubleProperty(stage.width)
        preferenceService.registerProperty(widthPropWrapper, "width", this.javaClass, id)
        stage.width = widthPropWrapper.get()
        widthPropWrapper.bind(stage.widthProperty())

        val heightPropWrapper = SimpleDoubleProperty(stage.height)
        preferenceService.registerProperty(heightPropWrapper, "height", this.javaClass, id)
        stage.height = heightPropWrapper.get()
        heightPropWrapper.bind(stage.heightProperty())

        val xPropWrapper = SimpleDoubleProperty(stage.x)
        preferenceService.registerProperty(xPropWrapper, "x", this.javaClass, id)
        stage.x = xPropWrapper.get()
        xPropWrapper.bind(stage.xProperty())

        val yPropWrapper = SimpleDoubleProperty(stage.y)
        preferenceService.registerProperty(yPropWrapper, "y", this.javaClass, id)
        stage.y = yPropWrapper.get()
        yPropWrapper.bind(stage.yProperty())

        val maximizedPropWrapper = SimpleBooleanProperty(stage.isMaximized)
        preferenceService.registerProperty(maximizedPropWrapper, "isMaximized", this.javaClass, id)
        stage.isMaximized = maximizedPropWrapper.get()
        maximizedPropWrapper.bind(stage.maximizedProperty())

        getStageHome(stage, id)
    }

    fun getStageHome(stage: Stage, id: String? = null) {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val sd = ge.screenDevices

        val rect = Rectangle(stage.x.roundToInt(), stage.y.roundToInt(), stage.width.roundToInt(), stage.height.roundToInt())

        for (gd in sd) {
            val bounds = gd.defaultConfiguration.bounds
            if (bounds.intersects(rect)) return
        }

        // stage out of screen bounds, return it home (and resize it just incase)

        stage.x = 0.0
        stage.y = 0.0
        stage.width = 1280.0
        stage.height = 720.0

        logger.warn("Stage ${if (id != null) "$id " else ""}was out of bounds, resetting to default position.")
    }

}