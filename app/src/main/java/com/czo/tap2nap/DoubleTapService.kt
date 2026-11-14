package com.czo.tap2nap

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/*
 * The service adds an invisible 1x1 px window with FLAG_WATCH_OUTSIDE_TOUCH. Android sends
 * it ACTION_OUTSIDE for every touch on the screen, without blocking it. We don't get the
 * position of the touch, only the time, but that's enough to spot a double tap.
 *
 * On a double tap we check that we're on the lock screen (without the PIN pad) or on the
 * home screen (without the app drawer or recent apps open), optionally read the proximity
 * sensor, and then lock with GLOBAL_ACTION_LOCK_SCREEN. That's the same as pressing the
 * power button, so fingerprint and face unlock still work.
 */
class DoubleTapService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var sensor: View? = null
    private var lastTapTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var proximityListener: SensorEventListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        addTouchSensor()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchSensor() {
        if (sensor != null) return
        val view = View(this)
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onScreenTouched(event.eventTime)
            }
            false
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            windowManager?.addView(view, params)
            sensor = view
        } catch (_: Exception) {
            // If this fails the service just does nothing.
        }
    }

    private fun onScreenTouched(time: Long) {
        val prefs = Prefs.get(this)
        val homeOn = prefs.getBoolean(Prefs.KEY_HOME, true)
        val lockOn = prefs.getBoolean(Prefs.KEY_LOCK, true)
        if (!homeOn && !lockOn) return

        val timeout = prefs.getInt(Prefs.KEY_TIMEOUT, Prefs.DEFAULT_TIMEOUT)
        val delta = time - lastTapTime

        if (lastTapTime != 0L && delta in MIN_GAP_MS..timeout) {
            lastTapTime = 0L
            if (isAllowedContext(homeOn, lockOn)) {
                val vibrate = prefs.getBoolean(Prefs.KEY_VIBRATE, false)
                if (prefs.getBoolean(Prefs.KEY_POCKET, true)) lockIfNotCovered(vibrate) else lockNow(vibrate)
            }
        } else {
            lastTapTime = time
        }
    }

    private fun isAllowedContext(homeOn: Boolean, lockOn: Boolean): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            return lockOn && !isBouncerShowing()
        }
        if (!homeOn) return false
        val root = rootInActiveWindow ?: return false
        val activePackage = root.packageName?.toString() ?: return false
        if (activePackage !in launcherPackages()) return false
        // Not while the app drawer or recent apps are open.
        return !isLauncherOverlayOpen(root)
    }

    // Looks for the app drawer or recent apps views of the Pixel Launcher (Launcher3).
    // When they're closed they aren't visible, so isVisibleToUser is false.
    private fun isLauncherOverlayOpen(root: AccessibilityNodeInfo): Boolean {
        val budget = intArrayOf(MAX_NODES_SCANNED_LAUNCHER)
        return hasVisibleId(root, LAUNCHER_OVERLAY_IDS, 0, budget)
    }

    private fun hasVisibleId(
        node: AccessibilityNodeInfo, ids: Set<String>, depth: Int, budget: IntArray
    ): Boolean {
        if (depth > 40 || budget[0]-- <= 0) return false
        val id = node.viewIdResourceName?.substringAfter(":id/")?.lowercase()
        if (id != null && id in ids && node.isVisibleToUser) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasVisibleId(child, ids, depth + 1, budget)) return true
        }
        return false
    }

    // Asked on every double tap, in case the default launcher changed.
    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val default = packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        // "android" means no default launcher has been chosen yet.
        if (default != null && default != "android") return setOf(default)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }   // FallbackHome, not a real launcher
            .toSet()
    }

    // True if the PIN, password or pattern pad is on screen, so typing the PIN fast
    // doesn't turn the screen off.
    private fun isBouncerShowing(): Boolean {
        val roots = try {
            windows.mapNotNull { it.root }
        } catch (_: Exception) {
            listOfNotNull(rootInActiveWindow)
        }
        val budget = intArrayOf(MAX_NODES_SCANNED)
        return roots.any { containsBouncer(it, 0, budget) }
    }

    private fun containsBouncer(node: AccessibilityNodeInfo, depth: Int, budget: IntArray): Boolean {
        if (depth > 40 || budget[0]-- <= 0) return false
        if (node.isPassword) return true
        val id = node.viewIdResourceName?.lowercase()
        if (id != null && BOUNCER_ID_HINTS.any { id.contains(it) }) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsBouncer(child, depth + 1, budget)) return true
        }
        return false
    }

    // Reads the proximity sensor once. If it's covered (pocket, bag) we do nothing.
    // If there's no sensor or it doesn't answer in time, we lock anyway.
    private fun lockIfNotCovered(vibrate: Boolean) {
        if (proximityListener != null) return  // already checking
        val sensorManager = getSystemService(SensorManager::class.java)
        val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensorManager == null || proximity == null) {
            lockNow(vibrate)
            return
        }
        val nearThreshold = minOf(proximity.maximumRange, 5f)

        var finished = false
        lateinit var timeout: Runnable
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (finished) return
                finished = true
                stop(this, timeout)
                val covered = event.values[0] < nearThreshold
                if (!covered) lockNow(vibrate)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        timeout = Runnable {
            if (!finished) {
                finished = true
                stop(listener, timeout)
                lockNow(vibrate)
            }
        }
        proximityListener = listener
        sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_FASTEST, handler)
        handler.postDelayed(timeout, PROXIMITY_TIMEOUT_MS)
    }

    private fun stop(listener: SensorEventListener, timeout: Runnable) {
        getSystemService(SensorManager::class.java)?.unregisterListener(listener)
        handler.removeCallbacks(timeout)
        proximityListener = null
    }

    private fun lockNow(vibrate: Boolean) {
        if (!vibrate) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            return
        }
        val view = sensor
        if (view == null) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            return
        }
        val strength = Prefs.get(this).getInt(Prefs.KEY_VIBRATION_STRENGTH, Haptics.MEDIUM)
        val length = Haptics.perform(view, strength)
        // Lock after the vibration ends, otherwise it can get cut off.
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, length)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            // The first tap opened or scrolled something, so it wasn't a double tap.
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> lastTapTime = 0L
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        removeSensor()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeSensor()
        super.onDestroy()
    }

    private fun removeSensor() {
        handler.removeCallbacksAndMessages(null)
        proximityListener?.let { getSystemService(SensorManager::class.java)?.unregisterListener(it) }
        proximityListener = null
        sensor?.let { v -> try { windowManager?.removeView(v) } catch (_: Exception) {} }
        sensor = null
    }

    companion object {
        // Two fingers at the same time don't count as a double tap.
        private const val MIN_GAP_MS = 40L
        private const val MAX_NODES_SCANNED = 600
        private const val PROXIMITY_TIMEOUT_MS = 300L
        private const val MAX_NODES_SCANNED_LAUNCHER = 1500

        // Pixel Launcher (Launcher3) views where a double tap is ignored.
        private val LAUNCHER_OVERLAY_IDS = setOf(
            // App drawer
            "apps_view", "apps_list_view", "all_apps_header", "search_container_all_apps",
            // Recent apps
            "overview_panel", "overview_actions_view", "clear_all"
        )

        // PIN / password / pattern view IDs seen on Pixel, Samsung, Xiaomi and AOSP.
        private val BOUNCER_ID_HINTS = listOf(
            "bouncer", "pinentry", "pin_view", "password_view", "passwordentry",
            "pattern_view", "lockpatternview", "sim_pin", "sim_puk", "key_enter",
            "numpad", "keyguard_pin", "keyguard_password", "keyguard_pattern"
        )
    }
}
