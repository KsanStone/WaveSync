package me.ksanstone.wavesync.wavesync.gui.window.jni

import com.sun.jna.Native
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser.WindowProc
import com.sun.jna.win32.W32APIOptions
import me.ksanstone.wavesync.wavesync.gui.window.data.TRACKMOUSEEVENT

interface User32Ex : User32 {
    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowlongptra
    fun SetWindowLongPtr(hWnd: HWND?, nIndex: Int, wndProc: WindowProc?): LONG_PTR?
    fun SetWindowLongPtr(hWnd: HWND?, nIndex: Int, wndProc: LONG_PTR?): LONG_PTR?

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-callwindowproca
    fun CallWindowProc(lpPrevWndFunc: LONG_PTR?, hWnd: HWND?, uMsg: Int, wParam: WPARAM?, lParam: LPARAM?): LRESULT?

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-iszoomed
    fun IsZoomed(hWnd: HWND?): Boolean

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-drawframecontrol
    fun DrawFrameControl(hdc: HDC?, rect: RECT?, uType: Int, uState: Int): Boolean

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getdpiforwindow
    fun GetDpiForWindow(hwnd: HWND?): Int

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getsystemmetricsfordpi
    fun GetSystemMetricsForDpi(nIndex: Int, dpi: Int): Int

    // https://docs.microsoft.com/de-DE/windows/win32/api/winuser/nf-winuser-getdcex
    fun GetDCEx(hWnd: HWND?, hrgnClip: HRGN?, flags: Int): HDC?

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-fillrect
    fun FillRect(hdc: HDC?, lprc: RECT?, hbr: HBRUSH?): Int

    // https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-adjustwindowrectexfordpi
    fun AdjustWindowRectExForDpi(lpRect: RECT?, dwStyle: Int, bMenu: Boolean, dwExStyle: Int, dpi: Int): Boolean

    // https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-trackmouseevent
    fun TrackMouseEvent(lpEventTrack: TRACKMOUSEEVENT?): Boolean

    // https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-screentoclient
    fun ScreenToClient(hWnd: HWND?, lpPoint: POINT?): Boolean

    companion object {
        @JvmField
        val INSTANCE: User32Ex = Native.load("user32", User32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}