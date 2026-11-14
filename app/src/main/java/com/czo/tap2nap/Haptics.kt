package com.czo.tap2nap

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

// Uses the system haptic feedback with FLAG_IGNORE_GLOBAL_SETTING, so it vibrates even
// on silent or with vibration turned off in the phone settings. The downside is that we
// can only pick between the system's built-in effects, not set the strength directly.
object Haptics {
    const val LIGHT = 0
    const val MEDIUM = 1
    const val STRONG = 2

    private const val REPEAT_GAP_MS = 70L

    // Returns roughly how long the vibration lasts, in ms.
    fun perform(view: View, strength: Int): Long {
        return when (strength) {
            LIGHT -> {
                val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                play(view, effect)
                20L
            }
            STRONG -> {
                // Strongest effect, played twice.
                play(view, HapticFeedbackConstants.LONG_PRESS)
                view.postDelayed({ play(view, HapticFeedbackConstants.LONG_PRESS) }, REPEAT_GAP_MS)
                REPEAT_GAP_MS + 40L
            }
            else -> {
                play(view, HapticFeedbackConstants.LONG_PRESS)
                40L
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun play(view: View, effect: Int) {
        view.performHapticFeedback(
            effect,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }
}
