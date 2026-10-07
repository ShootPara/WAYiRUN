package com.example.runningapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.runningapp.account.AccountView
import kotlinx.coroutines.delay

@Composable
fun AccountPanel(view: AccountView, enabled: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, showActions: Boolean = true) {
    val account = view.session
    var now by remember(account?.expiresAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(account?.expiresAtMs) {
        account?.let { delay((it.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0)); now = System.currentTimeMillis() }
    }
    if (account != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            account.pictureUrl?.let { AsyncImage(it, "Google profile picture", Modifier.size(48.dp).clip(CircleShape)) }
            Text(account.displayName ?: "Google account", style = MaterialTheme.typography.titleLarge)
        }
        if (showActions && account.expired(now)) Text("Your sign-in session has expired. Local running is still available.")
        if (showActions) OutlinedButton(onSignOut, enabled = enabled && !view.busy, modifier = Modifier.testTag("account-sign-out")) { Text("Sign out") }
    }
    if (showActions && (account == null || account.expired(now))) {
        OutlinedButton(onSignIn, enabled = enabled && !view.busy, modifier = Modifier.testTag("google-sign-in")) { Text("Sign in with Google") }
    }
    if (!showActions) return
    if (view.busy) Text("Connecting your account…")
    view.message?.let { Text(it, modifier = Modifier.testTag("account-message")) }
    Text("Completed runs sync to their original account. Offline tracking stays available.", style = MaterialTheme.typography.bodySmall)
}
