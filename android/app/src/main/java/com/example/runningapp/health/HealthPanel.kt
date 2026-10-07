package com.example.runningapp.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.runningapp.storage.RunDatabase

@Composable
fun HealthPanel(enabled: Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var status by remember {mutableStateOf("Connect to export your completed runs.")}
    var launchScope by rememberSaveable {mutableStateOf<String?>(null)}
    val request=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if(granted.containsAll(HealthAdapter.PERMISSIONS) && launchScope==HealthConnection.scope(context)) {
            scope.launch {
                RunDatabase.get(context).runs().retryHealth(HealthConnection.owner(context))
                if(launchScope==HealthConnection.scope(context)) {HealthConnection.connect(context);status="Exporting saved runs…"}
            }
        } else status="Permission was not granted. Your runs remain in WAYiRUN."
    }
    LaunchedEffect(Unit) {while(true) {
        status=context.getSharedPreferences("health-connect",0).getString("status",null) ?: status
        delay(3000)
    }}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Health Connect",style=MaterialTheme.typography.titleLarge)
        Text("Export saved and future completed runs for this account on this phone: exercise sessions, time and distance. WAYiRUN does not read other health apps' data.")
        Text(status)
        Button(enabled=enabled,onClick={
            try {
                if(HealthConnectClient.getSdkStatus(context)==HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                    context.startActivity(Intent(Intent.ACTION_VIEW,"market://details?id=com.google.android.apps.healthdata".toUri()))
                } else if(HealthAdapter.available(context)) {
                    launchScope=HealthConnection.scope(context);request.launch(HealthAdapter.PERMISSIONS)
                } else status="Health Connect is not supported on this phone. Running still works normally."
            }catch(_:Exception){status="Health Connect could not open. Your runs are safe."}
        }) {Text("Connect / retry export")}
        OutlinedButton(enabled=enabled,onClick={try {context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))}catch(_:Exception){status="Health Connect settings are unavailable."}}) {Text("Health Connect permissions")}
        Text("Deleting a run removes WAYiRUN's exported copy when permission is available. If permission is revoked, cleanup waits until you reconnect.",style=MaterialTheme.typography.bodySmall)
    }
}
