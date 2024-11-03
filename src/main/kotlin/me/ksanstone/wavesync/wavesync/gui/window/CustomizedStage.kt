package me.ksanstone.wavesync.wavesync.gui.window

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.WindowProc
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.robot.Robot
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.component.control.MainControl
import me.ksanstone.wavesync.wavesync.gui.window.data.NCCALCSIZE_PARAMS
import me.ksanstone.wavesync.wavesync.gui.window.data.TRACKMOUSEEVENT
import me.ksanstone.wavesync.wavesync.gui.window.jni.NativeUtilities.getHwnd
import me.ksanstone.wavesync.wavesync.gui.window.jni.NativeUtilities.getResizeHandleHeight
import me.ksanstone.wavesync.wavesync.gui.window.jni.NativeUtilities.isMaximized
import me.ksanstone.wavesync.wavesync.gui.window.jni.User32Ex

class CustomizedStage(private val stage: Stage, private val config: CaptionConfiguration) {
    private var hWnd: HWND? = null
    private var defWndProc: LONG_PTR? = null
    private var wndProc: WndProc? = null

    private val isRootReplaced = true
    private var isInjected = false

    private var closeButton: Node? = null
    private var restoreButton: Node? = null
    private var minimizeButton: Node? = null
    private var mainControl: MainControl? = null

    private fun snatchControls() {
        val a = stage.scene.root
        if (a is MainControl) {
            mainControl = a
            mainControl!!.setup(stage)
            closeButton = mainControl!!.controls.closeButton
            restoreButton = mainControl!!.controls.maximiseButton
            minimizeButton = mainControl!!.controls.minimizeButton
            config.setCaptionDragRegion(mainControl!!.dragRegion)
        }
    }

    fun inject() {
        snatchControls()

        this.isInjected = true

        this.hWnd = getHwnd(stage)
        this.wndProc = WndProc()
        this.defWndProc = User32Ex.INSTANCE.SetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC, wndProc)

        // trigger new WM_NCCALCSIZE message
        val rect = RECT()
        User32Ex.INSTANCE.GetWindowRect(hWnd, rect)
        User32Ex.INSTANCE.SetWindowPos(
            hWnd,
            null,
            rect.left,
            rect.top,
            rect.right - rect.left,
            rect.bottom - rect.top,
            WinUser.SWP_FRAMECHANGED
        )
    }

    fun release() {
        this.isInjected = false

        User32Ex.INSTANCE.SetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC, defWndProc)

        // trigger new WM_NCCALCSIZE message
        val rect = RECT()
        User32Ex.INSTANCE.GetWindowRect(hWnd, rect)
        User32Ex.INSTANCE.SetWindowPos(
            hWnd,
            null,
            rect.left,
            rect.top,
            rect.right - rect.left,
            rect.bottom - rect.top,
            WinUser.SWP_FRAMECHANGED
        )
    }

    private val closeBtnLocation: Bounds
        get() = closeButton!!.localToScreen(closeButton!!.boundsInLocal) ?: BoundingBox(0.0, 0.0, 0.0, 0.0)

    private val maximizeBtnLocation: Bounds
        get() = restoreButton!!.localToScreen(restoreButton!!.boundsInLocal) ?: BoundingBox(0.0, 0.0, 0.0, 0.0)

    private val minimizeBtnLocation: Bounds
        get() = minimizeButton!!.localToScreen(minimizeButton!!.boundsInLocal) ?: BoundingBox(0.0, 0.0, 0.0, 0.0)


    internal inner class WndProc : WindowProc {
        private var acitveButton: CaptionButton? = null

        override fun callback(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT? {
            return when (msg) {
                WindowEventParam.WM_NCCALCSIZE -> onWmNcCalcSize(hWnd, msg, wParam, lParam)
                WindowEventParam.WM_NCHITTEST -> onWmNcHitTest(hWnd, msg, wParam, lParam)
                WindowEventParam.WM_NCLBUTTONDOWN -> onWmNcLButtonDown(hWnd, msg, wParam, lParam)
                WindowEventParam.WM_NCMOUSEMOVE -> onWmNcMouseMove(hWnd, msg, wParam, lParam)
                WindowEventParam.WM_NCMOUSELEAVE, WindowEventParam.WM_MOUSELEAVE -> {
                    if (isRootReplaced) {
                        mainControl!!.hoverButton(null)
                        acitveButton = null
                    }
                    DefWndProc(hWnd, msg, wParam, lParam)
                }

                WinUser.WM_SIZE -> {
                    DefWndProc(hWnd, msg, wParam, lParam)
                }

                else -> DefWndProc(hWnd, msg, wParam, lParam)!!
            }
        }

        private fun onWmNcMouseMove(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT? {
            // when not using controls, this is not needed
            if (!isRootReplaced) return DefWndProc(hWnd, msg, wParam, lParam)

            val position = wParam.toInt()

            val newButton: CaptionButton? = when (position) {
                WindowEventParam.HTCLOSE -> CaptionButton.CLOSE
                WindowEventParam.HTMAXBUTTON -> CaptionButton.MAXIMIZE_RESTORE
                WindowEventParam.HTMINBUTTON -> CaptionButton.MINIMIZE
                else -> null
            }

            // continue only if a different button was hovered
            if (newButton == acitveButton) return LRESULT(0)
            acitveButton = newButton
            mainControl!!.hoverButton(acitveButton)

            if (acitveButton != null) {
                val ev = TRACKMOUSEEVENT()
                ev.cbSize = DWORD(ev.size().toLong())
                ev.dwFlags = DWORD((WindowEventParam.TME_LEAVE or WindowEventParam.TME_NONCLIENT).toLong())
                ev.hwndTrack = hWnd
                ev.dwHoverTime = DWORD(WindowEventParam.HOVER_DEFAULT.toLong())
                User32Ex.INSTANCE.TrackMouseEvent(ev)
                return LRESULT(0)
            }
            return DefWndProc(hWnd, msg, wParam, lParam)
        }

        private fun onWmNcLButtonDown(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
            val position = wParam.toInt()

            return when (position) {
                WindowEventParam.HTMINBUTTON -> {
                    User32Ex.INSTANCE.SendMessage(
                        hWnd,
                        WinUser.WM_SYSCOMMAND,
                        WPARAM(WinUser.SC_MINIMIZE.toLong()),
                        LPARAM(0)
                    )
                    LRESULT(0)
                }

                WindowEventParam.HTMAXBUTTON -> {
                    val maximized = isMaximized(hWnd)
                    User32Ex.INSTANCE.SendMessage(
                        hWnd,
                        WinUser.WM_SYSCOMMAND,
                        WPARAM((if (maximized) WindowEventParam.SC_RESTORE else WinUser.SC_MAXIMIZE).toLong()),
                        LPARAM(0)
                    )
                    LRESULT(0)
                }

                WindowEventParam.HTCLOSE -> {
                    User32Ex.INSTANCE.SendMessage(
                        hWnd,
                        WinUser.WM_SYSCOMMAND,
                        WPARAM(WindowEventParam.SC_CLOSE.toLong()),
                        LPARAM(0)
                    )
                    LRESULT(0)
                }

                else -> DefWndProc(hWnd, msg, wParam, lParam)!!
            }
        }

        private fun onWmNcHitTest(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
            // handle border interactions

            val rect = RECT()
            User32Ex.INSTANCE.GetClientRect(hWnd, rect)

            val screenX = GET_X_LPARAM(lParam)
            val screenY = GET_Y_LPARAM(lParam)

            val point = POINT(screenX, screenY)
            User32Ex.INSTANCE.ScreenToClient(hWnd, point)

            val res = DefWndProc(hWnd, msg, wParam, lParam)
            if (res!!.toLong() != WindowEventParam.HTCLIENT.toLong()) return res

            if (!isMaximized(hWnd)) if (point.y <= 3) return LRESULT(WindowEventParam.HTTOP.toLong())

            val captionBounds = config.dragRegion
            val mousePosScreen = Robot().mousePosition


            if (isRootReplaced) {
                // handle control buttons if controls are used
                val closeButtonBounds: Bounds = closeBtnLocation
                val maximizeButtonBounds: Bounds = maximizeBtnLocation
                val minimizeButtonBounds: Bounds = minimizeBtnLocation

                if (closeButtonBounds.contains(mousePosScreen)) {
                    return LRESULT(WindowEventParam.HTCLOSE.toLong())
                } else if (maximizeButtonBounds.contains(mousePosScreen)) {
                    return LRESULT(WindowEventParam.HTMAXBUTTON.toLong())
                } else if (minimizeButtonBounds.contains(mousePosScreen)) {
                    return LRESULT(WindowEventParam.HTMINBUTTON.toLong())
                }
            }

            // handle caption interaction
            if (captionBounds != null) {
                // custom caption was specified so use it
                if (captionBounds.contains(mousePosScreen)) return LRESULT(WindowEventParam.HTCAPTION.toLong())
            } else if (isRootReplaced) {
                // only apply this default caption if custom controls are used
                if (point.y < config.captionHeight) return LRESULT(WindowEventParam.HTCAPTION.toLong())
            }

            // no customized position detected -> in client area
            return LRESULT(WindowEventParam.HTCLIENT.toLong())
        }

        private fun onWmNcCalcSize(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
            if (wParam.toLong() == 0L) return LRESULT(0)

            val params = NCCALCSIZE_PARAMS(Pointer(lParam.toLong()))
            val oldTop = params.rgrc[0]!!.top

            val res = DefWndProc(hWnd, msg, wParam, lParam)
            if (res!!.toLong() != 0L) return res

            params.read()

            val newSize = params.rgrc[0]!!
            newSize.top = oldTop

            val maximized = isMaximized(hWnd)


            if (maximized && !stage.isFullScreen) {
                newSize.top += getResizeHandleHeight(hWnd)
            }

            params.write()
            return LRESULT(0)
        }

        private fun DefWndProc(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT? {
            return User32Ex.INSTANCE.CallWindowProc(defWndProc, hWnd, msg, wParam, lParam)
        }

        private fun HIWORD(lParam: LONG_PTR): Int {
            return ((lParam.toLong() shr 16) and 0xffffL).toInt()
        }

        private fun LOWORD(lParam: LONG_PTR): Int {
            return (lParam.toLong() and 0xffffL).toInt()
        }

        private fun GET_X_LPARAM(lParam: LONG_PTR): Int {
            return LOWORD(lParam).toShort().toInt()
        }

        private fun GET_Y_LPARAM(lParam: LONG_PTR): Int {
            return HIWORD(lParam).toShort().toInt()
        }
    }

    enum class CaptionButton {
        CLOSE, MINIMIZE, MAXIMIZE_RESTORE
    }
}

class WindowEventParam {
    companion object {
        const val WM_NCCALCSIZE = 0x0083
        const val WM_NCHITTEST = 0x0084
        const val WM_NCMOUSEMOVE = 0x00A0
        const val WM_NCLBUTTONDOWN = 0x00A1
        const val WM_MOUSELEAVE = 0x02A3
        const val WM_NCMOUSELEAVE = 0x02A2
        const val HTCLIENT = 1
        const val HTCAPTION = 2
        const val HTMAXBUTTON = 9
        const val HTCLOSE = 20
        const val HTMINBUTTON = 8
        const val HTTOP = 12
        const val SC_CLOSE = 0xF060
        const val SC_RESTORE = 0xF120
        const val TME_LEAVE = 0x00000002
        const val TME_NONCLIENT = 0x00000010
        const val HOVER_DEFAULT = -0x1
    }
}