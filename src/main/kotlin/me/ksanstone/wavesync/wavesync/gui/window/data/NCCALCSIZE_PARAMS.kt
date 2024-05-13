package me.ksanstone.wavesync.wavesync.gui.window.data

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.WinDef

@FieldOrder("rgrc", "lppos")
class NCCALCSIZE_PARAMS(p: Pointer?) : Structure(p) {
    @JvmField
    var rgrc: Array<WinDef.RECT?> = arrayOfNulls(3)
    @JvmField
    var lppos: Pointer? = null

    init {
        read()
    }
}