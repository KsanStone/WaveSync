package me.ksanstone.wavesync.wavesync.gui.window.jni

import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import javafx.scene.paint.Color
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.window.data.DWMWINDOWATTRIBUTE
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


    /**
     * Enables/disables the Immersive Dark Mode for a specified stage
     * officially only supported (documented) since Win 11 Build 22000
     * @param stage the stage to enable the Dark mode for
     * @param enabled if immersive dark mod should be enabled
     * @return if Immersive Dark Mode could be enabled successfully
     */
    fun setImmersiveDarkMode(stage: Stage, enabled: Boolean): Boolean {
        val hWnd = getHwnd(stage)
        val res: HRESULT = DwmApi.Companion.INSTANCE.DwmSetWindowAttribute(
            hWnd,
            DWMWINDOWATTRIBUTE.DWMWA_USE_IMMERSIVE_DARK_MODE,
            IntByReference(if (enabled) 1 else 0),
            4
        )
        return res.toLong() >= 0
    }

    /**
     * Sets the Caption Color of the specified Stage to the specified Color
     * this does only work since Win 11 Build 22000
     * @param stage the Stage to change the Caption Color
     * @param color the Color to use
     * @return if the change was successful
     */
    fun setCaptionColor(stage: Stage, color: Color): Boolean {
        val hWnd = getHwnd(stage)
        val red = (color.red * 255).toInt()
        val green = (color.green * 255).toInt()
        val blue = (color.blue * 255).toInt()
        // win api accepts the colors in reverse order
        val rgb = red + (green shl 8) + (blue shl 16)
        val res: HRESULT = DwmApi.Companion.INSTANCE.DwmSetWindowAttribute(
            hWnd,
            DWMWINDOWATTRIBUTE.DWMWA_CAPTION_COLOR,
            IntByReference(rgb),
            4
        )
        return res.toLong() >= 0
    }

    /**
     * sets the caption to the specified color if supported
     * if not supported uses immersive dark mode if color is mostly dark
     * @param stage the stage to modify
     * @param color the color to set the caption
     * @return if the stage was modified
     */
    fun customizeCation(stage: Stage, color: Color): Boolean {
        var success = setCaptionColor(stage, color)
        if (!success) {
            val red = (color.red * 255).toInt()
            val green = (color.green * 255).toInt()
            val blue = (color.blue * 255).toInt()
            val colorSum = red + green + blue

            val dark = colorSum < 255 * 3 / 2
            success = setImmersiveDarkMode(stage, dark)
        }
        return success
    }

    @JvmStatic
    fun isMaximized(hWnd: HWND?): Boolean {
        val windowStyle: LONG_PTR = User32Ex.Companion.INSTANCE.GetWindowLongPtr(hWnd, WinUser.GWL_STYLE)
        return (windowStyle.toLong() and WinUser.WS_MAXIMIZE.toLong()) == WinUser.WS_MAXIMIZE.toLong()
    }

    @JvmStatic
    fun getResizeHandleHeight(hWnd: HWND?): Int {
        val dpi: Int = User32Ex.Companion.INSTANCE.GetDpiForWindow(hWnd)
        return User32Ex.Companion.INSTANCE.GetSystemMetricsForDpi(WinUser.SM_CXPADDEDBORDER, dpi) +
                User32Ex.Companion.INSTANCE.GetSystemMetricsForDpi(WinUser.SM_CYSIZEFRAME, dpi)
    }
}