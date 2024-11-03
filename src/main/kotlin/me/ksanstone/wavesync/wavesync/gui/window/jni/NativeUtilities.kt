package me.ksanstone.wavesync.wavesync.gui.window.jni

import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import javafx.stage.Stage
import java.util.*

object NativeUtilities {
    /**
     * *should* return the HWND for the Specified Stage
     * might not, because JavaFX ist stupid and has no way
     * to do this
     * @param stage the Stage
     * @return hopefully the HWND for the correct stage
     */
    @JvmStatic
    fun getHwnd(stage: Stage): HWND {
        val randomId = UUID.randomUUID().toString()
        val title = stage.title
        stage.title = randomId
        val hWnd = User32.INSTANCE.FindWindow(null, randomId)
        stage.title = title
        return hWnd
    }


    @JvmStatic
    fun isMaximized(hWnd: HWND?): Boolean {
        val windowStyle: LONG_PTR = User32Ex.INSTANCE.GetWindowLongPtr(hWnd, WinUser.GWL_STYLE)
        return (windowStyle.toLong() and WinUser.WS_MAXIMIZE.toLong()) == WinUser.WS_MAXIMIZE.toLong()
    }

    @JvmStatic
    fun getResizeHandleHeight(hWnd: HWND?): Int {
        val dpi: Int = User32Ex.INSTANCE.GetDpiForWindow(hWnd)
        return User32Ex.INSTANCE.GetSystemMetricsForDpi(WinUser.SM_CXPADDEDBORDER, dpi) +
                User32Ex.INSTANCE.GetSystemMetricsForDpi(WinUser.SM_CYSIZEFRAME, dpi)
    }
}