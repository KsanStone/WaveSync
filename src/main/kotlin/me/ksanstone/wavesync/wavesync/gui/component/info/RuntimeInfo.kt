package me.ksanstone.wavesync.wavesync.gui.component.info

import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.statistic.Statistic
import me.ksanstone.wavesync.wavesync.service.statistic.StatisticBean
import me.ksanstone.wavesync.wavesync.service.statistic.StatisticMonitor

class RuntimeInfo : BorderPane() {

    private val statisticMonitor = WaveSyncBootApplication.applicationContext.getBean(StatisticMonitor::class.java)
    private val localizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    private val contentPane: FlowPane = FlowPane().apply {
        this.hgap = 10.0
        this.vgap = 10.0
    }

    init {
        parentProperty().addListener { _, _, v ->
            if (v != null) {
                updateProps(statisticMonitor.register(this))
            } else {
                statisticMonitor.unregister(this)
            }
        }
        this.stylesheets.add("/styles/runtime-info.css")
        center = contentPane
    }

    private fun updateProps(beans: List<StatisticBean>) {
        contentPane.children.clear()

        for (bean in beans) {
            val info = VBox().apply { this.spacing = 5.0; this.styleClass.add("infoList") }
            val category = VBox().apply { this.spacing = 5.0 }
            category.children.addAll(Label(localizationService.get(bean.getLocalizedNameKey())).apply {
                this.styleClass.add("titleLabel")
            }, info)

            bean.observableList().addListener(ListChangeListener {
                it.next()
                Platform.runLater { updateLabels(bean.observableList(), info) }
            })

            contentPane.children.add(category)
        }

    }

    private fun updateLabels(new: ObservableList<Statistic>, info: VBox) {
        info.children.setAll(new.map {
            HBox().apply {
                this.spacing = 5.0
                this.children.addAll(
                    Label(localizationService.get(it.localizedKey)).apply { this.styleClass.add("infoMetricName") },
                    Label(it.value).apply { this.styleClass.add("infoMetricValue") }
                )
            }
        })
    }
}