package me.ksanstone.wavesync.wavesync

import javafx.application.Preloader
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle


class WavePreloader : Preloader() {
    private var preloaderStage: Stage? = null

    @Throws(Exception::class)
    override fun start(primaryStage: Stage) {
        this.preloaderStage = primaryStage

        val image = Image("/icon.png")
        val imageView = ImageView(image)

        val root = StackPane(imageView)
        val scene = Scene(root)
        scene.fill = Color.TRANSPARENT

        primaryStage.initStyle(StageStyle.TRANSPARENT)
        primaryStage.icons.add(image)
        primaryStage.scene = scene
        primaryStage.width = imageView.prefWidth(-1.0)
        primaryStage.height = imageView.prefHeight(-1.0)
        val primScreenBounds: Rectangle2D = Screen.getPrimary().visualBounds
        primaryStage.x = (primScreenBounds.width - primaryStage.width) / 2
        primaryStage.y = (primScreenBounds.height - primaryStage.height) / 2
        primaryStage.show()
    }

    override fun handleApplicationNotification(info: PreloaderNotification) {
        preloaderStage!!.hide()
    }
}
