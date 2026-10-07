package com.example.runningapp.account

import android.app.Activity
import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runningapp.BuildConfig
import com.example.runningapp.storage.RunDatabase
import com.example.runningapp.sync.SyncScheduler
import com.example.runningapp.tracking.TrackingService
import com.example.runningapp.domain.RunState
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val api = AccountApi()
    private val credentials = CredentialManager.create(application)
    private val mutable = MutableStateFlow(AccountView(busy = true))
    val state = mutable.asStateFlow()
    var credentialUiActive = false
        private set
    init { viewModelScope.launch {
        mutable.value = AccountView(session = withContext(Dispatchers.IO) { store.read() })
        if (mutable.value.session != null) SyncScheduler.enqueue(application)
    } }

    fun signIn(activity: Activity) {
        if (mutable.value.busy || activeRun()) return
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.startsWith("__PRODUCTION_GOOGLE_") ||
            BuildConfig.GOOGLE_ANDROID_CLIENT_ID.startsWith("__PRODUCTION_GOOGLE_")) {
            mutable.value = mutable.value.copy(
                busy = false,
                message = "Production sign-in is not configured yet. Local running is still available.",
            )
            return
        }
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            var issued: AccountSession? = null
            try {
                val nonce = api.challenge()
                val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
                credentialUiActive = true
                val result = try {
                    credentials.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                } finally { credentialUiActive = false }
                val google = GoogleIdTokenCredential.createFrom(result.credential.data)
                val fresh = api.exchange(google.idToken, nonce)
                issued = fresh
                val previous = mutable.value.session
                withContext(Dispatchers.IO) { store.write(fresh) }
                mutable.value = AccountView(session = issued)
                runCatching { RunDatabase.get(getApplication()).runs().let { it.retry(fresh.ownerId); it.retryPull(fresh.ownerId); it.retryCoaching(fresh.ownerId) } }
                SyncScheduler.enqueue(getApplication())
                TrackingService.send(getApplication(), TrackingService.OPEN)
                // Replace only the account session; local runs retain their existing owner and contents.
                previous?.let { try { api.logout(it.token) } catch (_: Exception) { /* Old session expires. */ } }
            } catch (error: Exception) {
                issued?.let { try { api.logout(it.token) } catch (_: Exception) { /* Expires server-side. */ } }
                if (error is CancellationException) throw error
                val message = when (error) {
                    is GetCredentialCancellationException -> "Sign-in cancelled. You can still start a run."
                    is NoCredentialException -> "No Google account is available. Add one in phone Settings, then try again."
                    is AccountRequestException -> when (error.status) {
                        429 -> "Too many sign-in attempts. Wait a minute and try again."
                        401 -> "Google sign-in could not be verified. Please try again."
                        else -> "Sign-in is temporarily unavailable. You can still start a run."
                    }
                    is java.io.IOException -> "Can't connect. Check your internet connection; local running is still available."
                    else -> "Google sign-in couldn't finish. Try again; local running is still available."
                }
                mutable.value = mutable.value.copy(message = message)
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun signOut() {
        if (mutable.value.busy || activeRun()) return
        val session = mutable.value.session ?: return
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.clear() }
                mutable.value = AccountView(busy = true)
                TrackingService.send(getApplication(), TrackingService.OPEN)
                var remote = true
                try { api.logout(session.token) } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    remote = error is AccountRequestException && error.status == 401
                }
                try { credentials.clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) { /* Local session is gone. */ }
                mutable.value = AccountView(message = if (remote) "Signed out. Your local runs are still on this phone."
                    else "Signed out on this phone. The server session will expire within an hour. Your local runs are unchanged.")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = mutable.value.copy(message = "Sign-out couldn't finish. Please try again.")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private fun activeRun() = TrackingService.view.value.snapshot?.state in listOf(RunState.COUNTDOWN, RunState.RUNNING, RunState.PAUSED)
    fun importRuns(expectedOwner: String) {
        if (mutable.value.busy || activeRun() || mutable.value.session?.ownerId != expectedOwner) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val count = RunDatabase.get(getApplication()).runs().importLocal(expectedOwner)
                mutable.value = mutable.value.copy(message = "$count existing runs added to this account.")
                SyncScheduler.enqueue(getApplication())
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = "Import couldn't finish. Your runs are unchanged.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun retrySync() {
        val owner = mutable.value.session?.ownerId ?: return
        viewModelScope.launch {
            try { RunDatabase.get(getApplication()).runs().let { it.retry(owner); it.retryPull(owner); it.retryCoaching(owner) }; SyncScheduler.enqueue(getApplication()) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = "Couldn't schedule synchronization. Your runs are safe on this phone.") }
        }
    }

}
