package net.miettii.claudetwist.session

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

// Keeps the screen on behind the Claude app for as long as a session runs, and — when asked —
// swallows every touch that reaches it.
//
// An accessibility service can only read and press the window in front, which needs the screen on;
// FLAG_KEEP_SCREEN_ON on a window of this app's own keeps it on without touching the holder's screen
// timeout. The same window, made touchable, is what makes a pocket safe: this app presses controls
// through accessibility actions, never through touch, so nothing it does needs the touchscreen and
// nothing a thigh does reaches the Claude app. The notification shade draws above an application
// overlay, so the session is still stoppable with the shield up.
class ScreenAwakeOverlay(private val context: Context) {

    private val windows = context.getSystemService(WindowManager::class.java)
    private var shield: View? = null

    /** Whether the holder has granted the permission an overlay needs. */
    val isPermitted: Boolean get() = Settings.canDrawOverlays(context)

    /**
     * Raise the overlay. With [shieldTouches] it covers the screen, takes every touch and dims the
     * backlight to its minimum — nothing needs to be visible while the phone is pocketed. Without
     * it the overlay is a single pixel that only holds the screen on, leaving the Claude app usable.
     */
    fun raise(shieldTouches: Boolean) {
        if (shield != null || !isPermitted) return
        val layout = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                if (shieldTouches) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            width = if (shieldTouches) WindowManager.LayoutParams.MATCH_PARENT else 1
            height = if (shieldTouches) WindowManager.LayoutParams.MATCH_PARENT else 1
            if (shieldTouches) screenBrightness = MINIMUM_BACKLIGHT
        }
        shield = View(context).also { view ->
            runCatching { windows.addView(view, layout) }.onFailure { shield = null }
        }
    }

    fun lower() {
        val raised = shield ?: return
        shield = null
        runCatching { windows.removeView(raised) }
    }

    private companion object {
        // Above zero, which some devices read as "leave the backlight alone".
        const val MINIMUM_BACKLIGHT = 0.01f
    }
}
