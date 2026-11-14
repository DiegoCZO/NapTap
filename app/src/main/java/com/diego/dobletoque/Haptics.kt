package com.diego.dobletoque

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short vibration with three strengths (light / medium / strong).
 *
 * It's flagged as TOUCH feedback, like keyboard and button haptics: it follows the
 * "Touch feedback" setting (on by default), not the ring / notification / alarm vibration
 * settings, so it also works with the phone on silent.
 */
object Haptics {
    const val LIGHT = 0
    const val MEDIUM = 1
    const val STRONG = 2

    /** Vibrates and returns the vibration length in ms (0 if there's no vibrator). */
    fun vibrate(context: Context, strength: Int): Long {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return 0L

        // Duration (ms) and amplitude (1..255) for each strength.
        val (duration, amplitude) = when (strength) {
            LIGHT -> 20L to 80
            STRONG -> 60L to 255
            else -> 35L to 170
        }
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(duration, amplitude)
        } else {
            // No amplitude control: only the duration changes the feel.
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
        }
        return duration
    }
}
