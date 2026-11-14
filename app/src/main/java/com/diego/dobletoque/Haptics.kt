package com.diego.dobletoque

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Short vibration with three strengths (light / medium / strong).
 *
 * Uses the system's haptic feedback with FLAG_IGNORE_GLOBAL_SETTING: Android itself plays
 * the vibration, so it works even with the phone on silent and with touch feedback, ring,
 * notification and alarm vibration turned off. The trade-off: the strength can't be set
 * freely, only picked from the system's predefined effects.
 */
object Haptics {
    const val LIGHT = 0
    const val MEDIUM = 1
    const val STRONG = 2

    private const val REPEAT_GAP_MS = 70L

    /** Plays the vibration on [view] and returns how long it lasts, in ms. */
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
                // The heaviest single effect, twice in a row.
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
