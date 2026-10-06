package com.example.runningapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.runningapp.BuildConfig

@Composable
fun PermissionSetup(statuses: List<Pair<String, String>>, requesting: Boolean, error: String?,
    onRequest: () -> Unit, onAppSettings: () -> Unit, onMusic: () -> Unit, onContinue: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Set up WAYiRUN", style = MaterialTheme.typography.headlineLarge)
                Text("Build ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                Text("Allow precise location while using the app for outdoor GPS, Physical Activity for steps, and Notifications for run controls. Steps also need your measured stride in Run settings.")
                statuses.forEach { (name, status) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text(status, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Button(onClick = onRequest, enabled = !requesting, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow tracking permissions")
                }
                Text("If Android no longer shows a permission prompt, use App permissions below. You can return here from Run settings.")
                OutlinedButton(onClick = onAppSettings, enabled = !requesting, modifier = Modifier.fillMaxWidth()) { Text("App permissions") }
                Text("Music linking uses Android notification access, separate from run notifications. WAYiRUN uses it to control YouTube Music and does not read or store notification messages.")
                OutlinedButton(onClick = onMusic, enabled = !requesting, modifier = Modifier.fillMaxWidth()) { Text("Enable YouTube Music controls") }
                Text("If Android says Restricted setting, music linking is blocked for this installation. This app cannot remove that system restriction. Tracking and audio cues still work without music linking.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = onContinue, enabled = !requesting, modifier = Modifier.fillMaxWidth()) { Text("Continue to WAYiRUN") }
                Text("Declining access does not block a run. Without usable location or steps, the timer still runs and distance is unavailable.")
            }
        }
    }
}
