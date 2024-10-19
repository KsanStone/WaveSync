package me.ksanstone.wavesync.wavesync.gui.component.util

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox

class Keybinding : HBox() {

    val keyCodes: ObservableList<KeyCode> = FXCollections.observableArrayList()

    init {
        keyCodes.addListener(ListChangeListener { c -> c.next(); layoutKeys() })
        stylesheets.add("styles/key-binding.css")
        layoutKeys()
    }

    private fun layoutKeys() {
        children.clear()

        for (keyCode in keyCodes) {
            children.add(Label(keyCode.toString()).also { label -> label.styleClass.add("keycode") })
        }

    }

}