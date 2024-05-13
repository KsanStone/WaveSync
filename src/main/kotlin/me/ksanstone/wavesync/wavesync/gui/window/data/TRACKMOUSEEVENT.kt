package me.ksanstone.wavesync.wavesync.gui.window.data

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND

@FieldOrder("cbSize", "dwFlags", "hwndTrack", "dwHoverTime")
class TRACKMOUSEEVENT : Structure {
    @JvmField
    var cbSize: DWORD? = null
    @JvmField
    var dwFlags: DWORD? = null
    @JvmField
    var hwndTrack: HWND? = null
    @JvmField
    var dwHoverTime: DWORD? = null

    constructor(p: Pointer?) : super(p) {
        read()
    }

    constructor() : super()
}