package me.ksanstone.wavesync.wavesync.gui.gradient.component

import javafx.beans.property.ObjectProperty
import javafx.beans.property.ObjectPropertyBase
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Stop
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.GradientSerializer
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import kotlin.jvm.optionals.getOrNull

class Temp : AnchorPane() {

    private val gradientSerializer = WaveSyncBootApplication.applicationContext.getBean(GradientSerializer::class.java)
    private val stops: ObservableList<Stop> = FXCollections.observableArrayList()

    val gradient: ObjectProperty<SGradient?> = object : ObjectPropertyBase<SGradient?>() {

        init {
            stops.addListener(ListChangeListener {
                while(it.next()) { /* ff updates */ }
                set(gradientSerializer.fromStops(stops).getOrNull())
            })
        }

        override fun get(): SGradient? {
            return gradientSerializer.fromStops(stops).getOrNull()
        }

        override fun set(newValue: SGradient?) {
            val newStops = newValue?.let { gradientSerializer.toStops(newValue) } ?: listOf()
            if (newStops != stops) {
                stops.setAll(newStops)
                fireValueChangedEvent()
            }
        }

        override fun getBean() = this@Temp
        override fun getName() = "GradientPickerGradient"
    }

}