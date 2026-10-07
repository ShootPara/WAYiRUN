package com.example.runningapp.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.net.toUri
import com.example.runningapp.tracking.playlistLink

internal fun openPlaylist(context: Context, link: String): String? {
    val valid = playlistLink(link) ?: return "Paste a YouTube Music playlist link."
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, valid.toUri())
            .setPackage("com.google.android.apps.youtube.music"))
        null
    } catch (_: android.content.ActivityNotFoundException) {
        "YouTube Music isn't available to open this playlist. You can still start your run."
    } catch (_: SecurityException) {
        "Android couldn't open YouTube Music. You can still start your run."
    }
}

@Composable
internal fun PlaylistSetup(saved: String, busy: Boolean, onSave: (String) -> Unit, onOpen: (String) -> String?) {
    var entry by rememberSaveable { mutableStateOf(saved) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val valid = playlistLink(entry)
    Text("Your music", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(entry, { entry = it; error = null; onSave(it) }, enabled = !busy,
        label = { Text("YouTube Music playlist link") }, singleLine = true,
        isError = entry.isNotBlank() && valid == null,
        modifier = Modifier.fillMaxWidth().testTag("playlist-link"))
    Text("In YouTube Music, share a playlist and copy its link. Open it here, choose Play, then return to start your run.")
    if (entry.isNotBlank() && valid == null) Text("Use an https://music.youtube.com/playlist?list=… link.", color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        valid?.let { link -> onSave(link); error = onOpen(link) }
    }, enabled = !busy && valid != null, modifier = Modifier.fillMaxWidth().testTag("open-playlist")) { Text("Open playlist") }
    if (saved.isNotEmpty() || entry.isNotEmpty()) TextButton(onClick = {
        entry = ""; error = null; onSave("")
    }, enabled = !busy) { Text("Clear playlist") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
