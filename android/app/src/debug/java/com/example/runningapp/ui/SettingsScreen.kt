package com.example.runningapp.ui

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.runningapp.BuildConfig
import com.example.runningapp.account.AccountView
import com.example.runningapp.tracking.ensureTrackingChannel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    busy: Boolean,
    prefs: SharedPreferences,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    accountView: AccountView,
    accountEnabled: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onImport: (String) -> Unit,
    onRetrySync: () -> Unit,
    onPermissions: () -> Unit,
    onAppPermissions: () -> Unit,
    settingsError: String?,
) {
    var units by rememberSaveable { mutableStateOf(prefs.getString("units", "").orEmpty()) }
    var countdown by rememberSaveable { mutableFloatStateOf(prefs.getInt("countdown", 0).toFloat()) }
    var stride by rememberSaveable { mutableStateOf(prefs.getString("stride-entry", "").orEmpty()) }
    var strideUnit by rememberSaveable { mutableStateOf(prefs.getString("stride-unit", "cm").orEmpty()) }
    var playlist by rememberSaveable { mutableStateOf(prefs.getString("music-playlist", "").orEmpty()) }
    var announcementsEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("announcements-enabled", true)) }
    var autoPauseEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("auto-pause-enabled", true)) }
    var announcementSelection by remember { mutableStateOf(AnnouncementPreferences.read(prefs)) }
    val context = LocalContext.current
    val weatherLinks = LocalUriHandler.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCategory("run-tracking", "🏃", "Run Tracking", "Countdown, stride and auto-pause") {
            SettingsRow("⏸️", "Auto-pause when stopped", "Pause automatically when movement evidence shows you stopped") {
                Switch(autoPauseEnabled, { enabled ->
                    autoPauseEnabled = enabled
                    prefs.edit { putBoolean("auto-pause-enabled", enabled) }
                }, Modifier.testTag("auto-pause-enabled"))
            }
            SettingsDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("⏱️  Countdown · ${countdown.roundToInt()} seconds", fontWeight = FontWeight.SemiBold)
                Text("Delay before active run timing begins", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(countdown, { value ->
                    countdown = value
                    prefs.edit { putInt("countdown", value.roundToInt()) }
                }, valueRange = 0f..10f, steps = 9, modifier = Modifier.testTag("countdown-setting"))
            }
            SettingsDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("👟  Indoor stride length", fontWeight = FontWeight.SemiBold)
                Text("Measured distance per counted step; leave blank for GPS or time-only tracking",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompactChoices(listOf("cm", "inches"), strideUnit, "stride-unit") { next ->
                    val value = stride.toDoubleOrNull()
                    if (value != null && value.isFinite()) {
                        stride = String.format(Locale.US, "%.3f",
                            if (next == strideUnit) value else if (next == "cm") value * 2.54 else value / 2.54)
                    }
                    strideUnit = next
                    prefs.edit { putString("stride-entry", stride); putString("stride-unit", strideUnit) }
                }
                OutlinedTextField(stride, { value ->
                    stride = value
                    prefs.edit { putString("stride-entry", value) }
                }, modifier = Modifier.fillMaxWidth().testTag("stride"),
                    label = { Text("Distance per step ($strideUnit)") }, singleLine = true,
                    isError = stride.isNotBlank() && (stride.toDoubleOrNull()?.let { !it.isFinite() || it <= 0 } != false),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        if (stride.isNotBlank() && (stride.toDoubleOrNull()?.let { !it.isFinite() || it <= 0 } != false)) {
                            Text("Enter a positive stride length, or leave it blank.")
                        }
                    })
            }
        }

        SettingsCategory("audio-milestones", "🔊", "Audio & Milestones", "Voice feedback and run milestones") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                AnnouncementSettings(announcementsEnabled, announcementSelection, units == "Kilometers", { enabled ->
                    announcementsEnabled = enabled
                    prefs.edit { putBoolean("announcements-enabled", enabled) }
                }, { selection ->
                    announcementSelection = selection
                    AnnouncementPreferences.write(prefs, selection)
                })
            }
        }

        SettingsCategory("run-display", "📱", "Run Display", "Units and appearance") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📏  Distance units", fontWeight = FontWeight.SemiBold)
                CompactChoices(listOf("Miles", "Kilometers"), units, "distance-units") { selected ->
                    units = selected
                    prefs.edit { putString("units", selected) }
                }
            }
            SettingsDivider()
            SettingsRow("🌙", "Dark mode", "Use WAYiRUN's dark appearance") {
                Switch(dark, onDark, Modifier.testTag("dark-setting"))
            }
        }

        SettingsCategory("after-run", "📸", "After Your Run", "Photos, weather and finish choices") {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Finish choices", fontWeight = FontWeight.SemiBold)
                Text("Photo, coaching and sharing choices appear after each run. Runs and photos stay private unless you explicitly share them.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Weather data by Open-Meteo.com", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { weatherLinks.openUri("https://open-meteo.com/") })
                Text("CC BY 4.0 · Rounded hourly model estimate; emoji representation.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { weatherLinks.openUri("https://creativecommons.org/licenses/by/4.0/") })
            }
        }

        SettingsCategory("connected-services", "🔗", "Connected Services", "Health, music and AI integrations") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                PlaylistSetup(playlist, busy, { link ->
                    playlist = link
                    prefs.edit { putString("music-playlist", link) }
                }, { link -> openPlaylist(context, link) })
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                com.example.runningapp.health.HealthPanel(accountEnabled)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                CoachingKeyPanel(accountView, accountEnabled)
            }
        }

        SettingsCategory("account-app", "⚙️", "Account & App", "Profile, synchronization and permissions") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                AccountPanel(accountView, accountEnabled, onSignIn, onSignOut)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                SyncPanel(accountView, accountEnabled, onImport, onRetrySync)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Permissions", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onPermissions) { Text("Request missing permissions") }
                TextButton(onClick = onAppPermissions) { Text("App permissions") }
                settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ensureTrackingChannel(context)))
                }) { Text("Tracking notification settings") }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("About WAYiRUN", style = MaterialTheme.typography.titleLarge)
                Text("Build ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun SettingsCategory(
    id: String,
    emoji: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("settings-category-$id"), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .testTag("settings-category-$id-header")
            .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 27.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("⌄", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 180f else 0f).padding(start = 8.dp))
        }
        if (expanded) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsRow(emoji: String, name: String, description: String, control: @Composable () -> Unit) {
    val stack = LocalDensity.current.fontScale >= 1.5f
    if (stack) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsLabel(emoji, name, description)
            Box(Modifier.align(Alignment.End)) { control() }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SettingsLabel(emoji, name, description) }
            Spacer(Modifier.width(12.dp))
            control()
        }
    }
}

@Composable
private fun SettingsLabel(emoji: String, name: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 21.sp, modifier = Modifier.width(38.dp))
        Column {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactChoices(options: List<String>, selected: String, tag: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected == option, { onSelect(option) }, label = { Text(option) },
                modifier = Modifier.weight(1f).testTag("$tag-$option"))
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
