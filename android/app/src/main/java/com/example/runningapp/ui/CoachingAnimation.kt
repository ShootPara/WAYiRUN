package com.example.runningapp.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CoachingAnimation(label: String, dismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "Coaching")
    val scale by transition.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "Pulse")
    Column(Modifier.fillMaxWidth().heightIn(min = 300.dp).testTag("coaching-animation").clickable(onClick = dismiss),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
        CircularProgressIndicator(Modifier.size(80.dp).scale(scale))
        Text(label, style = MaterialTheme.typography.headlineSmall)
        Text("Your run is saved.")
        Text("Tap to see your summary. Audio keeps playing.")
    }
}
