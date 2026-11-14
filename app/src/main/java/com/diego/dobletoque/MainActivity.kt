package com.diego.dobletoque

import android.app.Activity
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Settings screen.
class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(false)
    private var themeMode by mutableStateOf(Prefs.THEME_SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LauncherIcon.ensureVisible(this)
        serviceEnabled = isServiceEnabled()
        // Show the guided setup the first time, unless the service is already on.
        showOnboarding = !serviceEnabled &&
            !Prefs.get(this).getBoolean(Prefs.KEY_ONBOARDING_DONE, false)
        themeMode = Prefs.get(this).getString(Prefs.KEY_THEME, Prefs.THEME_SYSTEM) ?: Prefs.THEME_SYSTEM
        setContent {
            val dark = when (themeMode) {
                Prefs.THEME_LIGHT -> false
                Prefs.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            // Status bar icons have to follow the app theme, not the system one.
            LaunchedEffect(dark) { setSystemBars(dark) }

            Tap2NapTheme(dark) {
                if (showOnboarding) {
                    OnboardingScreen(serviceEnabled = serviceEnabled) {
                        Prefs.get(this).edit().putBoolean(Prefs.KEY_ONBOARDING_DONE, true).apply()
                        showOnboarding = false
                    }
                } else {
                    SettingsScreen(
                        serviceEnabled = serviceEnabled,
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            themeMode = mode
                            Prefs.get(this).edit().putString(Prefs.KEY_THEME, mode).apply()
                        }
                    )
                }
            }
        }
    }

    private fun setSystemBars(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent)
        else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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

@Composable
private fun Tap2NapTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    serviceEnabled: Boolean,
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var update by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var dialogRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    // Check for updates when the screen opens. The popup shows once per version.
    LaunchedEffect(Unit) {
        val latest = UpdateChecker.fetchLatestAsync()
        if (latest != null && latest.isNewer) {
            update = latest
            if (prefs.getString(Prefs.KEY_DISMISSED_VERSION, null) != latest.version) {
                dialogRelease = latest
            }
        }
    }

    dialogRelease?.let { release ->
        UpdateDialog(
            release = release,
            onLater = {
                prefs.edit().putString(Prefs.KEY_DISMISSED_VERSION, release.version).apply()
                dialogRelease = null
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
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
            // First, so it's easy to find for someone who doesn't read the current language.
            Section(stringResource(R.string.section_appearance)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LanguageItem()
                    HorizontalDivider()
                }
                ThemeItem(themeMode, onThemeChange)
            }

            StatusCard(serviceEnabled)

            update?.let { release -> UpdateCard(release) { dialogRelease = release } }

            Section(stringResource(R.string.section_where)) {
                PrefSwitch(
                    stringResource(R.string.home_title), stringResource(R.string.home_summary),
                    Prefs.KEY_HOME, true
                )
                HorizontalDivider()
                PrefSwitch(
                    stringResource(R.string.lock_title), stringResource(R.string.lock_summary),
                    Prefs.KEY_LOCK, true
                )
            }

            Section(stringResource(R.string.section_options)) {
                PrefSwitch(
                    stringResource(R.string.pocket_title), stringResource(R.string.pocket_summary),
                    Prefs.KEY_POCKET, true
                )
                HorizontalDivider()
                var vibrateOn by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_VIBRATE, false)) }
                PrefSwitch(
                    stringResource(R.string.vibrate_title), stringResource(R.string.vibrate_summary),
                    Prefs.KEY_VIBRATE, false
                ) { on ->
                    vibrateOn = on
                    if (on) {
                        Haptics.perform(
                            view, prefs.getInt(Prefs.KEY_VIBRATION_STRENGTH, Haptics.MEDIUM)
                        )
                    }
                }
                if (vibrateOn) VibrationStrengthItem()
            }

            Section(stringResource(R.string.section_speed)) {
                var timeout by remember {
                    mutableFloatStateOf(prefs.getInt(Prefs.KEY_TIMEOUT, Prefs.DEFAULT_TIMEOUT).toFloat())
                }
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.speed_label, timeout.roundToInt()))
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
                        stringResource(R.string.speed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Section(stringResource(R.string.section_restricted)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.restricted_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { openAppInfo(context) }) {
                        Text(stringResource(R.string.btn_app_info))
                    }
                }
            }

            Section(stringResource(R.string.section_about)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge
                    )
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
                                updateMessage = context.getString(R.string.checking)
                                scope.launch {
                                    val latest = UpdateChecker.fetchLatestAsync()
                                    checking = false
                                    updateMessage = when {
                                        latest == null -> context.getString(R.string.check_failed)
                                        latest.isNewer -> {
                                            update = latest
                                            dialogRelease = latest
                                            context.getString(R.string.check_available, latest.version)
                                        }
                                        else -> context.getString(R.string.check_up_to_date)
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.btn_check_updates)) }
                        TextButton(onClick = { openUrl(context, UpdateChecker.repoUrl) }) {
                            Text(stringResource(R.string.btn_github))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

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
                stringResource(if (enabled) R.string.status_on_title else R.string.status_off_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(if (enabled) R.string.status_on_body else R.string.status_off_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            val open = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (enabled) {
                FilledTonalButton(onClick = open) { Text(stringResource(R.string.btn_accessibility_settings)) }
            } else {
                Button(onClick = open) { Text(stringResource(R.string.btn_turn_on)) }
            }
        }
    }
}

@Composable
private fun UpdateCard(release: UpdateChecker.Release, onUpdate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.update_available_title, release.version),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.update_you_have, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(onClick = onUpdate) { Text(stringResource(R.string.btn_update)) }
        }
    }
}

private enum class UpdatePhase { Ready, NeedsPermission, Downloading, Installing, Failed }

// Update popup with the changelog and an Install button.
@Composable
private fun UpdateDialog(release: UpdateChecker.Release, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdatePhase.Ready) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val downloading = phase == UpdatePhase.Downloading

    fun startInstall() {
        val apkUrl = release.apkUrl
        if (apkUrl == null) {
            // No APK in the release, open the page instead.
            openUrl(context, release.pageUrl)
            return
        }
        if (!AppUpdater.canInstall(context)) {
            phase = UpdatePhase.NeedsPermission
            openInstallPermissionSettings(context)
            return
        }
        phase = UpdatePhase.Downloading
        progress = null
        scope.launch {
            val apk = AppUpdater.download(context, apkUrl) { p -> progress = p }
            if (apk == null) {
                phase = UpdatePhase.Failed
                return@launch
            }
            phase = if (AppUpdater.install(context, apk)) UpdatePhase.Installing else UpdatePhase.Failed
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        title = { Text(stringResource(R.string.new_version_title, release.version)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.update_you_have, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.whats_new), fontWeight = FontWeight.SemiBold)
                val noNotes = stringResource(R.string.no_notes)
                Text(release.notes.ifBlank { noNotes })

                when (phase) {
                    UpdatePhase.Ready -> {}
                    UpdatePhase.NeedsPermission -> HintText(stringResource(R.string.permission_hint))
                    UpdatePhase.Downloading -> {
                        val p = progress
                        if (p != null) {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.downloading, (p * 100).toInt()))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.downloading_unknown))
                        }
                    }
                    UpdatePhase.Installing -> HintText(stringResource(R.string.installing_hint))
                    UpdatePhase.Failed -> {
                        HintText(stringResource(R.string.download_failed), error = true)
                        TextButton(onClick = { openUrl(context, release.downloadUrl) }) {
                            Text(stringResource(R.string.btn_open_browser))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !downloading, onClick = { startInstall() }) {
                Text(stringResource(R.string.btn_install))
            }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onLater) {
                Text(stringResource(R.string.btn_later))
            }
        }
    )
}

@Composable
private fun HintText(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
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

// Tapping an option also plays it, so you can feel the difference.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VibrationStrengthItem() {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { Prefs.get(context) }
    var strength by remember {
        mutableIntStateOf(prefs.getInt(Prefs.KEY_VIBRATION_STRENGTH, Haptics.MEDIUM))
    }
    val labels = listOf(R.string.strength_light, R.string.strength_medium, R.string.strength_strong)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Text(stringResource(R.string.vibration_strength_title), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.vibration_strength_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = strength == index,
                    onClick = {
                        strength = index
                        prefs.edit().putInt(Prefs.KEY_VIBRATION_STRENGTH, index).apply()
                        Haptics.perform(view, index)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size)
                ) { Text(stringResource(label)) }
            }
        }
    }
}

// First launch: explain why we need Accessibility (Google Play asks for this), then
// help turn the service on.
@Composable
private fun OnboardingScreen(serviceEnabled: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val total = 2

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.onb_step, step + 1, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (step == 0) {
                Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.onb_disclosure_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(stringResource(R.string.onb_disclosure_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_agree))
                }
                TextButton(
                    onClick = { (context as? Activity)?.finish() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.btn_not_now)) }
            } else if (!serviceEnabled) {
                Text(stringResource(R.string.onb_enable_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb_enable_body), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.btn_open_accessibility)) }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.onb_restricted_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(stringResource(R.string.onb_restricted_body), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { openAppInfo(context) }) {
                            Text(stringResource(R.string.btn_app_info))
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.onb_done_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb_done_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_start))
                }
            }
        }
    }
}

// A list item that opens a dialog to pick one option. Options are (value, label) pairs.
@Composable
private fun ChoiceItem(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(options.firstOrNull { it.first == selected }?.second ?: options[0].second)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDialog = false
                                    if (value != selected) onSelect(value)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

// Same setting as Settings > Apps > Tap2Nap > Language (Android 13+).
@Composable
private fun LanguageItem() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(currentAppLanguage(context)) }
    ChoiceItem(
        title = stringResource(R.string.language_title),
        options = listOf(
            "" to stringResource(R.string.language_system),
            "en" to stringResource(R.string.language_english),
            "es" to stringResource(R.string.language_spanish)
        ),
        selected = current
    ) { tag ->
        current = tag
        setAppLanguage(context, tag)
    }
}

@Composable
private fun ThemeItem(themeMode: String, onThemeChange: (String) -> Unit) {
    ChoiceItem(
        title = stringResource(R.string.theme_title),
        options = listOf(
            Prefs.THEME_SYSTEM to stringResource(R.string.theme_system),
            Prefs.THEME_LIGHT to stringResource(R.string.theme_light),
            Prefs.THEME_DARK to stringResource(R.string.theme_dark)
        ),
        selected = themeMode,
        onSelect = onThemeChange
    )
}

// Empty means the system language.
private fun currentAppLanguage(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return ""
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
    return if (locales == null || locales.isEmpty) "" else locales[0].language
}

private fun setAppLanguage(context: Context, tag: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(LocaleManager::class.java)?.applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

// Opens "Install unknown apps" for Tap2Nap.
private fun openInstallPermissionSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    } catch (_: Exception) {
        openAppInfo(context)
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    )
}
