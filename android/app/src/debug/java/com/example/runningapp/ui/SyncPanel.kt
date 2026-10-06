package com.example.runningapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.runningapp.account.AccountView
import com.example.runningapp.storage.RunDatabase

@Composable
fun SyncPanel(account: AccountView, enabled: Boolean, onImport: (String) -> Unit, onRetry: () -> Unit,
    daoOverride: com.example.runningapp.storage.RunDao? = null) {
    val context = LocalContext.current
    val dao = daoOverride ?: remember { RunDatabase.get(context).runs() }
    val local by remember { dao.localCount() }.collectAsState(initial = 0)
    val owner = account.session?.ownerId
    val operations by remember(owner) { dao.syncStatus(owner.orEmpty()) }.collectAsState(initial = emptyList())
    val pull by remember(owner) { dao.pullStatus(owner.orEmpty()) }.collectAsState(initial = null)
    val photosPending by remember(owner) { dao.pendingPhotoCountFlow(owner.orEmpty()) }.collectAsState(initial = 0)
    val photoError by remember(owner) { dao.photoErrorFlow(owner.orEmpty()) }.collectAsState(initial = null)
    var confirm by remember(owner) { mutableStateOf(false) }
    Text("Cloud sync", style = MaterialTheme.typography.titleLarge)
    if (owner == null) {
        Text("Sign in to synchronize new runs. Existing runs stay on this phone until you choose to add them.")
        return
    }
    val pending = operations.count { it.status in listOf("PENDING", "AUTH", "BLOCKED") }
    Text("Runs: ${operations.count { it.status == "SYNCED" }} synced · $pending pending")
    if (photosPending > 0) {
        Text("Photos waiting to upload: $photosPending")
        photoError?.let { Text(com.example.runningapp.photos.photoSyncMessage(it)) }
    }
    pull?.error?.let { Text(it) }
    if (pull?.status == "PENDING") Text(if (pull!!.nextAttemptMs > System.currentTimeMillis() && pull!!.error == null)
        "Cloud runs checked. Restore checks continue automatically." else "Checking this account's cloud runs for restore.")
    operations.firstOrNull { it.error != null }?.error?.let { Text(it) }
    if (operations.any { it.action == "DELETE" && it.status != "DELETED" }) {
        Text("Cloud removal is pending. Stay signed in to this account and reconnect to finish it.")
    }
    OutlinedButton(onRetry, enabled = !account.busy, modifier = Modifier.testTag("retry-sync")) { Text("Retry sync") }
    if (local > 0) {
        OutlinedButton({ confirm = true }, enabled = enabled && !account.busy, modifier = Modifier.testTag("import-runs")) {
            Text("Add existing runs to this account")
        }
        Text("$local completed runs are stored only on this phone.")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Add existing runs?") },
        text = { Text("Add $local completed runs to ${account.session?.displayName ?: "this Google account"}? Their account ownership cannot be changed afterward.") },
        confirmButton = { TextButton({ confirm = false; onImport(owner) }, enabled = enabled && !account.busy) { Text("Add runs") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}
