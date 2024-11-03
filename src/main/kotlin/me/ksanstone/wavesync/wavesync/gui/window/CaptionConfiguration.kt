package me.ksanstone.wavesync.wavesync.gui.window

class CaptionConfiguration(
    var captionHeight: Int = 31,
) {
    var dragRegion: DragRegion? = null

    /**
     * Specify a [DragRegion] to define where the window should be
     * draggable
     * @param captionDragRegion the [DragRegion]
     */
    fun setCaptionDragRegion(captionDragRegion: DragRegion?): CaptionConfiguration {
        this.dragRegion = captionDragRegion
        return this
    }

}