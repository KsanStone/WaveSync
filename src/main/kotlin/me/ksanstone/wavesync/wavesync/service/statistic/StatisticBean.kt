package me.ksanstone.wavesync.wavesync.service.statistic

import javafx.collections.ObservableList

interface StatisticBean {

    fun getLocalizedNameKey(): String

    fun observableList(): ObservableList<Statistic>

    fun getStatistics(): List<Statistic>

    fun start()

    fun pause()

}