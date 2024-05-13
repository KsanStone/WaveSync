package me.ksanstone.wavesync.wavesync.gui.window.data

/**
 * https://docs.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute
 */
object DWMWINDOWATTRIBUTE {
    const val DWMWA_NCRENDERING_ENABLED: Int = 0
    const val DWMWA_NCRENDERING_POLICY: Int = 1
    const val DWMWA_TRANSITIONS_FORCEDISABLED: Int = 2
    const val DWMWA_ALLOW_NCPAINT: Int = 3
    const val DWMWA_CAPTION_BUTTON_BOUNDS: Int = 4
    const val DWMWA_NONCLIENT_RTL_LAYOUT: Int = 5
    const val DWMWA_FORCE_ICONIC_REPRESENTATION: Int = 6
    const val DWMWA_FLIP3D_POLICY: Int = 7
    const val DWMWA_EXTENDED_FRAME_BOUNDS: Int = 8
    const val DWMWA_HAS_ICONIC_BITMAP: Int = 9
    const val DWMWA_DISALLOW_PEEK: Int = 10
    const val DWMWA_EXCLUDED_FROM_PEEK: Int = 11
    const val DWMWA_CLOAK: Int = 12
    const val DWMWA_CLOAKED: Int = 13
    const val DWMWA_FREEZE_REPRESENTATION: Int = 14
    const val DWMWA_PASSIVE_UPDATE_MODE: Int = 15
    const val DWMWA_USE_HOSTBACKDROPBRUSH: Int = 16
    const val DWMWA_USE_IMMERSIVE_DARK_MODE: Int = 20
    const val DWMWA_WINDOW_CORNER_PREFERENCE: Int = 33
    const val DWMWA_BORDER_COLOR: Int = 34
    const val DWMWA_CAPTION_COLOR: Int = 35
    const val DWMWA_TEXT_COLOR: Int = 36
    const val DWMWA_VISIBLE_FRAME_BORDER_THICKNESS: Int = 37
    const val DWMWA_LAST: Int = 38
}