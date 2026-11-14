package com.diego.dobletoque

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short vibration with three strengths (light / medium / strong). */
object Haptics {
    const val LIGHT = 0
    const val MEDIUM = 1
    const val STRONG = 2

    fun vibrate(context: Context, strength: Int) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        // Duration (ms) and amplitude (1..255) for each strength.
        val (duration, amplitude) = when (strength) {
            LIGHT -> 18L to 70
            STRONG -> 55L to 255
            else -> 30L to 160
        }
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(duration, amplitude)
        } else {
            // No amplitude control: only the duration changes the feel.
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}
