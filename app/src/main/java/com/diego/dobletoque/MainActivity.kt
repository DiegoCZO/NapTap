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

/** Settings screen, built in code so it doesn't depend on any library. */
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

        root.addView(sectionTitle("Where it works"))
        root.addView(switch("Home screen (launcher)", Prefs.KEY_HOME, true))
        root.addView(switch("Lock screen", Prefs.KEY_LOCK, true))
        root.addView(switch("Vibrate when turning off", Prefs.KEY_VIBRATE, false))

        root.addView(sectionTitle("Double tap speed"))
        val timeoutLabel = TextView(this)
        val current = prefs.getInt(Prefs.KEY_TIMEOUT, Prefs.DEFAULT_TIMEOUT)
        timeoutLabel.text = "Max time between taps: $current ms"
        root.addView(timeoutLabel)
        root.addView(SeekBar(this).apply {
            max = Prefs.MAX_TIMEOUT - Prefs.MIN_TIMEOUT
            progress = current - Prefs.MIN_TIMEOUT
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val value = p + Prefs.MIN_TIMEOUT
                    timeoutLabel.text = "Max time between taps: $value ms"
                    prefs.edit().putInt(Prefs.KEY_TIMEOUT, value).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(sectionTitle("Can't turn it on?"))
        root.addView(TextView(this).apply {
            text = "On Android 13+, accessibility is \"restricted\" for sideloaded apps. " +
                "Open App info → ⋮ menu (top right) → \"Allow restricted settings\", " +
                "then try turning it on again."
        })
        root.addView(Button(this).apply {
            text = "Open app info"
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
        status.text = if (on) "✅ Service is on" else "⚠️ Service is off"
        enableButton.text = if (on) "Accessibility settings" else "Turn on in Accessibility"
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
