package com.example.runningapp.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.runningapp.BuildConfig
import com.example.runningapp.account.SessionStore
import com.example.runningapp.photos.LocalExternalRunAction
import com.example.runningapp.storage.RunDatabase
import com.example.runningapp.storage.RunPublication
import com.example.runningapp.sync.CloudSyncApi
import com.example.runningapp.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun queueRunPublication(context: Context, id: String, shared: Boolean? = null, photo: Boolean? = null): RunPublication {
    val dao = RunDatabase.get(context).runs()
    val run = requireNotNull(dao.get(id))
    require(run.cloudOwnerId == null || run.cloudOwnerId == SessionStore(context).read()?.ownerId)
    return dao.queuePublication(id, run.cloudOwnerId, shared, photo).also { SyncScheduler.enqueue(context) }
}

internal fun publicationStatus(value: RunPublication?): String = when {
    value == null -> "Loading sharing status..."
    value.wantShared == false -> "Unshare pending confirmation"
    value.ownerId == null && value.wantShared == true -> "Share pending. Add this run to your account in settings."
    value.wantShared == true -> "Share pending sync"
    value.wantPhoto != null -> "Photo display change pending sync"
    value.error == "CONFLICT" -> "Sharing changed elsewhere. Review and try again."
    !value.known && value.ownerId != null -> "Sharing status awaiting sync"
    value.shared -> "Shared"
    else -> "Private"
}

internal fun publicationShareIntent(link: String): Intent {
    require(isPublicationLink(link))
    return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link)
    }, "Share run link")
}

internal fun deliverPublication(context: Context, delivery: String, link: String, external: (String?) -> Unit, runId: String) {
    require(isPublicationLink(link))
    if (delivery == "copy") {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Run link", link))
    } else {
        require(delivery == "share")
        external(runId)
        context.startActivity(publicationShareIntent(link))
    }
}

internal fun isPublicationLink(link: String): Boolean =
    Regex("${Regex.escape(BuildConfig.API_ORIGIN)}/r/[0-9a-f]{32}").matches(link)

@Composable
fun RunSharing(id: String) {
    key(id) { RunSharingContent(id) }
}

@Composable
private fun RunSharingContent(id: String) {
    val context = LocalContext.current
    val dao = remember { RunDatabase.get(context).runs() }
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val external = LocalExternalRunAction.current
    val state by remember(id) { dao.publicationFlow(id) }.collectAsState(initial = null)
    var accessible by remember(id) { mutableStateOf(false) }
    var message by remember(id) { mutableStateOf<String?>(null) }
    var busy by remember(id) { mutableStateOf(false) }
    var request by remember(id) { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { request++; busy = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(id) {
        var previousToken: String? = null
        var initialized = false
        while (true) {
            val account = store.read()
            val run = dao.get(id)
            accessible = run != null && run.state == "FINISHED" && (run.cloudOwnerId == null || run.cloudOwnerId == account?.ownerId)
            if (accessible && (!initialized || previousToken != account?.token)) {
                try {
                    dao.preparePublication(id, run!!.cloudOwnerId, refresh = true)
                    SyncScheduler.enqueue(context)
                    initialized = true
                    message = null
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { accessible = false }
            }
            previousToken = account?.token
            delay(1000)
        }
    }
    fun change(shared: Boolean? = null, photo: Boolean? = null, delivery: String? = null) {
        val ticket = ++request
        scope.launch {
            busy = true; message = null
            val token = store.read()?.token
            try {
                val intent = queueRunPublication(context, id, shared, photo)
                if (delivery != null && intent.ownerId != null) {
                    val sync = PublicationSync(dao, CloudSyncApi(), { store.read() })
                    // At most photo preference plus share; no background completion launches a chooser.
                    sync.runOnce(id)
                    val intermediate = dao.publication(id)
                    if (intermediate?.intentId == intent.intentId && intermediate.error == null && intermediate.wantShared == true)
                        sync.runOnce(id)
                    val latest = dao.publication(id) ?: return@launch
                    if (ticket != request || token != store.read()?.token ||
                        !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                        latest.intentId != intent.intentId || dao.get(id)?.cloudOwnerId != intent.ownerId) return@launch
                    val link = latest.publicUrl
                    if (latest.known && latest.shared && latest.wantShared == null && latest.wantPhoto == null && latest.error == null && link != null) {
                        deliverPublication(context, delivery, link, external, id)
                        if (delivery == "copy") message = "Link copied"
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                external(null)
                if (ticket == request) message = "Action not completed. Check sharing status and try again."
            } finally { if (ticket == request) busy = false }
        }
    }
    if (accessible) SharingControls(state, message, busy) { shared, photo, delivery -> change(shared, photo, delivery) }
}

@Composable
internal fun SharingControls(state: RunPublication?, message: String?, busy: Boolean,
    change: (Boolean?, Boolean?, String?) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("run-sharing"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sharing", style = MaterialTheme.typography.titleMedium)
        Text(message ?: publicationStatus(state), Modifier.testTag("publication-status"))
        OutlinedButton(enabled = !busy, onClick = { change(true, null, "share") }) { Text("Share run link") }
        OutlinedButton(enabled = !busy, onClick = { change(true, null, "copy") }) { Text("Copy run link") }
        OutlinedButton(onClick = { change(false, null, null) }) { Text("Unshare") }
        Row {
            Checkbox(checked = state?.wantPhoto ?: state?.photoVisible ?: true, enabled = !busy,
                onCheckedChange = { change(null, it, null) }, modifier = Modifier.testTag("publication-photo"))
            Text("Display photo with shared run", Modifier.weight(1f).padding(top = 12.dp))
        }
    }
}
