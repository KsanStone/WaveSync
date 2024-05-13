package me.ksanstone.wavesync.wavesync.gui.window.jni

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference

interface DwmApi : Library {
    // https://docs.microsoft.com/en-us/windows/win32/api/dwmapi/nf-dwmapi-dwmsetwindowattribute
    fun DwmSetWindowAttribute(hWnd: HWND?, dwAttribute: Int, pvAttribute: IntByReference?, cbAttribute: Int): HRESULT

    companion object {
        val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java)
    }
}