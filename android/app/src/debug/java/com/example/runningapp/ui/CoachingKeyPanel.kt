package com.example.runningapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.runningapp.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CoachingKeyPanel(account: AccountView, enabled: Boolean, api: CoachingKeys = remember { CoachingKeyApi() }) {
    Text("AI coaching", style = MaterialTheme.typography.titleLarge)
    val session = account.session
    if (session == null || session.expired()) {
        Text("Sign in to save your own OpenAI API key for coaching.")
        return
    }
    key(session.ownerId, session.token) { KeySettings(session, enabled && !account.busy, api) }
}

@Composable
private fun KeySettings(session: AccountSession, enabled: Boolean, api: CoachingKeys) {
    var status by remember { mutableStateOf<CoachingKeyStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        busy = true
        try { status = api.status(session); message = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = null; message = coachingKeyError(e) }
        finally { busy = false }
    }
    Text(if (status?.configured == true) "OpenAI key saved ••••••••" else if (status == null) "Key status not loaded" else "No OpenAI key saved",
        modifier = Modifier.testTag("coaching-key-status"))
    status?.checkedAt?.let { Text("Credentials checked ${DateFormat.getDateTimeInstance().format(Date(it))}. Text and voice access are not yet tested.", style = MaterialTheme.typography.bodySmall) }
    if (status?.available == false) Text("Secure key storage is currently unavailable. You can still remove a saved key.")
    Text("Stored encrypted for this Google account across phones. Selected coaching sends all saved data, including GPS, from this run and your previous run to OpenAI using your API credits. Cedar coaching is AI-generated; offline encouragement uses onboard recordings.", style = MaterialTheme.typography.bodySmall)
    if (busy) Text("Updating key settings…")
    message?.let { Text(it, Modifier.testTag("coaching-key-message")) }
    Row {
        OutlinedButton(onClick = { refresh++ }, enabled = enabled && !busy) { Text("Refresh key status") }
    }
    Row {
        OutlinedButton(onClick = { editing = true }, enabled = enabled && !busy && status?.available == true,
            modifier = Modifier.testTag("coaching-key-edit")) { Text(if (status?.configured == true) "Replace key" else "Add API key") }
        if (status?.configured == true) TextButton(onClick = { removing = true }, enabled = enabled && !busy) { Text("Remove key") }
    }
    if (editing) KeyEntryDialog(session.displayName ?: "your Google account", busy,
        onCancel = { if (!busy) editing = false }, onSave = { value ->
            val revision = status?.revision ?: return@KeyEntryDialog
            busy = true
            scope.launch {
                try { status = api.change(session, revision, value); editing = false; message = "Key saved. Coaching is selected by the checkbox when you finish a run." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { editing = false; status = null; message = coachingKeyError(e) }
                finally { busy = false }
            }
        })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("Remove saved OpenAI key?") },
        text = { Text("This removes it from WAYiRUN for this account. It does not revoke the key at OpenAI or delete your runs.") },
        confirmButton = { TextButton(onClick = {
            val revision = status?.revision ?: return@TextButton
            removing = false; busy = true
            scope.launch {
                try { status = api.change(session, revision, null); message = "Saved key removed." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = null; message = coachingKeyError(e) }
                finally { busy = false }
            }
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } })
}

@Composable
private fun KeyEntryDialog(account: String, busy: Boolean, onCancel: () -> Unit, onSave: (String) -> Unit) {
    // Deliberately not rememberSaveable: the secret must never enter saved-state backups.
    var entered by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onCancel, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("OpenAI key for $account") }, text = {
            Column {
                Text("Paste your own OpenAI API key. WAYiRUN sends it securely to its server and checks it with OpenAI. ChatGPT subscriptions do not include API usage.")
                OutlinedTextField(value = entered, onValueChange = { if (it.length <= 512) entered = it },
                    label = { Text("API key") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.testTag("coaching-key-input"))
            }
        }, confirmButton = { TextButton(onClick = { val value = entered.trim(); entered = ""; onSave(value) },
            enabled = !busy && entered.trim().length in 19..512) { Text(if (busy) "Checking…" else "Save key") } },
        dismissButton = { TextButton(onClick = { entered = ""; onCancel() }, enabled = !busy) { Text("Cancel") } })
}
