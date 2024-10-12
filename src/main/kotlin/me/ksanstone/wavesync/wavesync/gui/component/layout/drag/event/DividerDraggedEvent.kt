package me.ksanstone.wavesync.wavesync.gui.component.layout.drag.event

import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode

data class DividerDraggedEvent(
    val node: DragLayoutNode,
    val dividerId: Int
) : DragLayoutEvent