package com.diego.dobletoque

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Settings screen (Material 3 / Jetpack Compose, with the wallpaper's dynamic colours). */
class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NapTapTheme {
                SettingsScreen(serviceEnabled = serviceEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isServiceEnabled()
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
}

// ---------------------------------------------------------------------------------------
// Theme

@Composable
private fun NapTapTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

// ---------------------------------------------------------------------------------------
// Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(serviceEnabled: Boolean) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var update by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // On open: check for updates and, if update notifications are on, ask for permission.
    LaunchedEffect(Unit) {
        if (prefs.getBoolean(Prefs.KEY_UPDATE_NOTIFY, true)) askNotificationPermissionIfNeeded()
        val latest = UpdateChecker.fetchLatestAsync()
        if (latest != null && latest.isNewer) update = latest
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Nap Tap") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatusCard(serviceEnabled)

            update?.let { release -> UpdateCard(release) }

            Section("Where it works") {
                PrefSwitch("Home screen", "Your default launcher", Prefs.KEY_HOME, true)
                HorizontalDivider()
                PrefSwitch("Lock screen", "Ignored while the PIN pad is showing", Prefs.KEY_LOCK, true)
            }

            Section("Options") {
                PrefSwitch(
                    "Pocket protection",
                    "Do nothing if the proximity sensor is covered",
                    Prefs.KEY_POCKET, true
                )
                HorizontalDivider()
                PrefSwitch("Vibrate", "Short vibration when the screen turns off", Prefs.KEY_VIBRATE, false)
                HorizontalDivider()
                PrefSwitch(
                    "Update notifications",
                    "Check GitHub once a day for a new version",
                    Prefs.KEY_UPDATE_NOTIFY, true
                ) { on -> if (on) askNotificationPermissionIfNeeded() }
            }

            Section("Double tap speed") {
                var timeout by remember {
                    mutableFloatStateOf(prefs.getInt(Prefs.KEY_TIMEOUT, Prefs.DEFAULT_TIMEOUT).toFloat())
                }
                Column(Modifier.padding(16.dp)) {
                    Text("Max time between taps: ${timeout.roundToInt()} ms")
                    Slider(
                        value = timeout,
                        onValueChange = { timeout = it },
                        onValueChangeFinished = {
                            prefs.edit().putInt(Prefs.KEY_TIMEOUT, timeout.roundToInt()).apply()
                        },
                        valueRange = Prefs.MIN_TIMEOUT.toFloat()..Prefs.MAX_TIMEOUT.toFloat(),
                        steps = (Prefs.MAX_TIMEOUT - Prefs.MIN_TIMEOUT) / 25 - 1
                    )
                    Text(
                        "Lower = you need to tap faster",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Section("Can't turn it on?") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "On Android 13+, accessibility is restricted for apps installed " +
                            "outside the Play Store. Open App info → ⋮ menu (top right) → " +
                            "Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { openAppInfo(context) }) { Text("Open app info") }
                }
            }

            Section("About") {
                Column(Modifier.padding(16.dp)) {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    updateMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        TextButton(
                            enabled = !checking,
                            onClick = {
                                checking = true
                                updateMessage = "Checking…"
                                scope.launch {
                                    val latest = UpdateChecker.fetchLatestAsync()
                                    checking = false
                                    updateMessage = when {
                                        latest == null -> "Couldn't check (no internet or no releases yet)"
                                        latest.isNewer -> { update = latest; "Version ${latest.version} is available" }
                                        else -> "You're up to date"
                                    }
                                }
                            }
                        ) { Text("Check for updates") }
                        TextButton(onClick = { openUrl(context, UpdateChecker.repoUrl) }) {
                            Text("GitHub")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------
// Components

@Composable
private fun StatusCard(enabled: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (enabled) "Nap Tap is on" else "Nap Tap is off",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (enabled) "Double tap the home or lock screen to turn the screen off."
                else "Turn on the Nap Tap accessibility service to start.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            val open = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (enabled) {
                FilledTonalButton(onClick = open) { Text("Accessibility settings") }
            } else {
                Button(onClick = open) { Text("Turn on") }
            }
        }
    }
}

@Composable
private fun UpdateCard(release: UpdateChecker.Release) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Update available: ${release.version}", style = MaterialTheme.typography.titleMedium)
            Text(
                "You have ${BuildConfig.VERSION_NAME}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(onClick = { openUrl(context, release.downloadUrl) }) { Text("Download") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun PrefSwitch(
    title: String,
    summary: String?,
    key: String,
    default: Boolean,
    onChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var checked by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else null,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    prefs.edit().putBoolean(key, it).apply()
                    onChange(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ---------------------------------------------------------------------------------------
// Helpers

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    )
}
