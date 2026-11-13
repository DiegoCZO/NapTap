package com.diego.dobletoque

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Pantalla de ajustes, armada por código para no depender de ninguna librería. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)
        val pad = dp(20)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        })

        status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(status)

        enableButton = Button(this).apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(enableButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(sectionTitle("Dónde funciona"))
        root.addView(switch("Pantalla de inicio (launcher)", Prefs.KEY_HOME, true))
        root.addView(switch("Pantalla de bloqueo", Prefs.KEY_LOCK, true))
        root.addView(switch("Vibrar al apagar", Prefs.KEY_VIBRATE, false))

        root.addView(sectionTitle("Velocidad del doble toque"))
        val timeoutLabel = TextView(this)
        val current = prefs.getInt(Prefs.KEY_TIMEOUT, Prefs.DEFAULT_TIMEOUT)
        timeoutLabel.text = "Máximo entre toques: $current ms"
        root.addView(timeoutLabel)
        root.addView(SeekBar(this).apply {
            max = Prefs.MAX_TIMEOUT - Prefs.MIN_TIMEOUT
            progress = current - Prefs.MIN_TIMEOUT
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val value = p + Prefs.MIN_TIMEOUT
                    timeoutLabel.text = "Máximo entre toques: $value ms"
                    prefs.edit().putInt(Prefs.KEY_TIMEOUT, value).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(sectionTitle("¿No te deja activarlo?"))
        root.addView(TextView(this).apply {
            text = "En Android 13+ las apps instaladas a mano tienen la accesibilidad " +
                "\"restringida\". Abrí Info de la app → menú ⋮ (arriba a la derecha) → " +
                "\"Permitir ajustes restringidos\", y después volvé a activarlo."
        })
        root.addView(Button(this).apply {
            text = "Abrir info de la app"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = if (on) "✅ Servicio activo" else "⚠️ Servicio desactivado"
        enableButton.text = if (on) "Ajustes de accesibilidad" else "Activar en Accesibilidad"
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, DoubleTapService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        return splitter.any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun switch(label: String, key: String, default: Boolean) = Switch(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isChecked = Prefs.get(context).getBoolean(key, default)
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, checked ->
            Prefs.get(context).edit().putBoolean(key, checked).apply()
        }
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.START
        isAllCaps = true
        setPadding(0, dp(24), 0, dp(4))
        alpha = 0.7f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
