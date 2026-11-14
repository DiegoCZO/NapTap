package com.diego.dobletoque

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

/**
 * How it works:
 *
 * 1. A 1x1 px accessibility overlay with FLAG_WATCH_OUTSIDE_TOUCH is added. Android
 *    sends it an ACTION_OUTSIDE every time a finger touches the screen outside of it
 *    (i.e. anywhere), without blocking or consuming the touch: the launcher and the
 *    lock screen keep working normally. For privacy Android doesn't provide the
 *    coordinates, only the time of the touch, which is all we need.
 *
 * 2. When two touches arrive less than X ms apart, we check where we are:
 *      - locked (keyguard) → is "lock screen" enabled? is the PIN/password pad hidden?
 *      - unlocked → is the active window the default launcher, with the app drawer closed?
 *
 * 3. With pocket protection on, the proximity sensor is read once: if something covers
 *    the screen (pocket, bag), nothing happens.
 *
 * 4. If so, performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) turns the screen off.
 *    This locks just like the power button, so fingerprint / face unlock keep working
 *    (unlike "device admin", which forces the PIN).
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
        LauncherIcon.showOnFirstRun(this)
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
            // If this fails the service stays idle, but nothing breaks.
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
        // Don't turn the screen off while the app drawer or recent apps are open.
        return !isLauncherOverlayOpen(root)
    }

    /**
     * Is the app drawer or the recent apps screen open? Looks for their Launcher3 /
     * Pixel Launcher views among the views visible on screen. When closed, they are
     * off-screen or hidden, so isVisibleToUser returns false.
     */
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

    /** Launcher package(s). Queried on every double tap in case the launcher changes. */
    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val default = packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        // "android" = system chooser: no default launcher has been picked yet.
        if (default != null && default != "android") return setOf(default)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }   // Settings' FallbackHome
            .toSet()
    }

    /**
     * Is the PIN / password / pattern pad showing? If so, do nothing, so that typing the
     * PIN quickly doesn't turn the screen off.
     */
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

    /**
     * Reads the proximity sensor once. Covered (pocket, bag, face down on something) →
     * do nothing. No sensor or no reading in time → lock anyway.
     */
    private fun lockIfNotCovered(vibrate: Boolean) {
        if (proximityListener != null) return  // a check is already running
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
        if (vibrate) {
            val strength = Prefs.get(this).getInt(Prefs.KEY_VIBRATION_STRENGTH, Haptics.MEDIUM)
            Haptics.vibrate(this, strength)
        }
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            // If the first tap opened an app / notification / widget, or scrolled something
            // (e.g. a list inside a widget), it's not a double tap.
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
        /** Ignore near-simultaneous touches (two fingers at once). */
        private const val MIN_GAP_MS = 40L
        private const val MAX_NODES_SCANNED = 600
        private const val PROXIMITY_TIMEOUT_MS = 300L
        private const val MAX_NODES_SCANNED_LAUNCHER = 1500

        /**
         * View IDs in Launcher3 / Pixel Launcher (and launchers based on it) where a double
         * tap must be ignored: the app drawer and the recent apps (overview) screen.
         */
        private val LAUNCHER_OVERLAY_IDS = setOf(
            // App drawer
            "apps_view", "apps_list_view", "all_apps_header", "search_container_all_apps",
            // Recent apps
            "overview_panel", "overview_actions_view", "clear_all"
        )

        /** Common PIN/password/pattern view IDs on AOSP, Pixel, Samsung, Xiaomi… */
        private val BOUNCER_ID_HINTS = listOf(
            "bouncer", "pinentry", "pin_view", "password_view", "passwordentry",
            "pattern_view", "lockpatternview", "sim_pin", "sim_puk", "key_enter",
            "numpad", "keyguard_pin", "keyguard_password", "keyguard_pattern"
        )
    }
}
