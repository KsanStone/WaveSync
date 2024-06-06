package me.ksanstone.wavesync.wavesync.service

import javafx.application.Platform
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ChangeListener
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt


@Service
class StageSizingService(
    private val preferenceService: PreferenceService,
    private val stageManager: StageManager
) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    private val registeredStages = mutableMapOf<String, StageRegistration>()

    fun registerStageSize(stage: Stage, id: String) {
        if (stage.isShowing) doRegister(stage, id)
        else doRegisterWhenShown(stage, id)
    }

    private fun doRegisterWhenShown(stage: Stage, id: String) {
        lateinit var listener: ChangeListener<Boolean>
        listener = ChangeListener<Boolean> { _, _, v ->
            if (!v) return@ChangeListener
            Platform.runLater {
                doRegister(
                    stage,
                    id
                ); stage.showingProperty().removeListener(listener)
            }
        }
        stage.showingProperty().addListener(listener)
    }

    fun registerAndBindProperty(
        stage: Stage,
        propertyName: String,
        id: String,
        preferenceService: PreferenceService
    ): DoubleProperty {
        val getterMethod: Method = stage.javaClass.getMethod("get${propertyName.methodize()}")
        val setterMethod: Method = stage.javaClass.getMethod("set${propertyName.methodize()}", Double::class.java)

        val property = SimpleDoubleProperty().apply {
            val fieldValue = getterMethod.invoke(stage) as Double
            set(fieldValue)
        }

        preferenceService.registerProperty(property, propertyName, this.javaClass, id)
        setterMethod.invoke(stage, property.get())

        property.bind(stage.javaClass.getMethod("${propertyName}Property").invoke(stage) as ReadOnlyDoubleProperty)

        return property
    }

    private fun doRegister(stage: Stage, id: String) {
        Platform.runLater {
            registeredStages[id] = StageRegistration(
                stage,
                widthProperty = registerAndBindProperty(stage, "width", id, preferenceService),
                heightProperty = registerAndBindProperty(stage, "height", id, preferenceService),
                xProperty = registerAndBindProperty(stage, "x", id, preferenceService),
                yProperty = registerAndBindProperty(stage, "y", id, preferenceService),
            )
            getStageHome(stage, id)

            CompletableFuture.runAsync {
                Thread.sleep(500)
                Platform.runLater {
                    val maximizedPropWrapper = SimpleBooleanProperty(stage.isMaximized)
                    preferenceService.registerProperty(maximizedPropWrapper, "isMaximized", this.javaClass, id)
                    stage.isMaximized = maximizedPropWrapper.get()
                    maximizedPropWrapper.bind(stage.maximizedProperty())
                }
            }
        }
    }

    fun getStageHome(stage: Stage, id: String? = null) {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val sd = ge.screenDevices

        val rect =
            Rectangle(stage.x.roundToInt(), stage.y.roundToInt(), stage.width.roundToInt(), stage.height.roundToInt())

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

    fun unregisterStage(id: String) {
        registeredStages.remove(id)?.let {
            it.widthProperty.unbind()
            it.heightProperty.unbind()
            it.xProperty.unbind()
            it.yProperty.unbind()
            preferenceService.unregisterObjectTree(this.javaClass, id)
            stageManager.releaseStage(it.stage)
        }
    }

    fun findId(stage: Stage): String? {
        return registeredStages.entries.find { it.value.stage == stage }?.key
    }

}

/**
 * Capitalize but not deprecated
 */
fun String.methodize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

data class StageRegistration(
    val stage: Stage,
    val widthProperty: DoubleProperty,
    val heightProperty: DoubleProperty,
    val xProperty: DoubleProperty,
    val yProperty: DoubleProperty,
)