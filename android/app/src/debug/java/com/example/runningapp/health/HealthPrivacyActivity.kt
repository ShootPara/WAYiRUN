package com.example.runningapp.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthPrivacyActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {MaterialTheme {Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("WAYiRUN and Health Connect",style=MaterialTheme.typography.headlineMedium)
            Text("With your permission, WAYiRUN writes your completed running sessions, recorded pauses and distance to Health Connect on this device. Your run history remains saved in WAYiRUN.")
            Text("This integration does not read other apps' health records, upload Health Connect data, or share photos, GPS routes or coaching with Health Connect. Apps you separately authorize in Health Connect may access your exported records.")
            Text("You can revoke write permission in Health Connect. Deleting a run queues removal of the records WAYiRUN exported; cleanup resumes when permission is available. Revoking permission does not itself erase previously exported records.")
            Button(onClick={finish()}) {Text("Done")}
        }}}}
    }
}
