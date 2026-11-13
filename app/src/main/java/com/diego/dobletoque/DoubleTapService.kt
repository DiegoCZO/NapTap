package com.diego.dobletoque

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Cómo funciona:
 *
 * 1. Se agrega una ventana de accesibilidad de 1x1 px con FLAG_WATCH_OUTSIDE_TOUCH.
 *    Android le manda un ACTION_OUTSIDE cada vez que el dedo toca la pantalla fuera de
 *    ella (o sea, en cualquier lado), sin bloquear ni consumir el toque: el launcher y
 *    la lockscreen siguen funcionando normal. Por privacidad Android no da las
 *    coordenadas, solo el momento del toque, que es lo único que necesitamos.
 *
 * 2. Si llegan dos toques separados por menos de X ms, se mira dónde estamos:
 *      - pantalla bloqueada (keyguard)  → ¿está activado "lockscreen"? ¿no se está
 *        escribiendo el PIN/contraseña?
 *      - desbloqueado → ¿la ventana activa es el launcher por defecto?
 *
 * 3. Si corresponde, performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) apaga la pantalla.
 *    Esto bloquea igual que el botón de encendido, así que la huella / desbloqueo facial
 *    siguen funcionando (no es como "device admin", que fuerza el PIN).
 */
class DoubleTapService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var sensor: View? = null
    private var lastTapTime = 0L

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
            // Si falla, el servicio queda inactivo pero no rompe nada.
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
            if (isAllowedContext(homeOn, lockOn)) lockNow(prefs.getBoolean(Prefs.KEY_VIBRATE, false))
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
        val activePackage = rootInActiveWindow?.packageName?.toString() ?: return false
        return activePackage in launcherPackages()
    }

    /** Paquete(s) del launcher. Se consulta en cada doble toque por si cambiás de launcher. */
    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val default = packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        // "android" = selector del sistema: no hay launcher por defecto elegido todavía.
        if (default != null && default != "android") return setOf(default)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }   // FallbackHome de Ajustes
            .toSet()
    }

    /**
     * ¿Está a la vista el teclado del PIN / contraseña / patrón? Si es así no hacemos
     * nada, para que escribir el PIN rápido no apague la pantalla.
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

    private fun lockNow(vibrate: Boolean) {
        if (vibrate) {
            sensor?.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            // Si el primer toque abrió una app / notificación / atajo, no cuenta como doble toque.
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> lastTapTime = 0L
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
        sensor?.let { v -> try { windowManager?.removeView(v) } catch (_: Exception) {} }
        sensor = null
    }

    companion object {
        /** Ignora toques casi simultáneos (dos dedos a la vez). */
        private const val MIN_GAP_MS = 40L
        private const val MAX_NODES_SCANNED = 600

        /** IDs típicos del PIN/contraseña/patrón en AOSP, Pixel, Samsung, Xiaomi… */
        private val BOUNCER_ID_HINTS = listOf(
            "bouncer", "pinentry", "pin_view", "password_view", "passwordentry",
            "pattern_view", "lockpatternview", "sim_pin", "sim_puk", "key_enter",
            "numpad", "keyguard_pin", "keyguard_password", "keyguard_pattern"
        )
    }
}
