package com.example.runningapp.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.runningapp.domain.Achievement

@Composable
fun AchievementCelebration(awards: List<Achievement>, dismiss: () -> Unit) {
    var index by remember(awards) { mutableIntStateOf(0) }
    val transition=rememberInfiniteTransition(label="Achievement")
    val scale by transition.animateFloat(0.94f,1.06f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="Celebrate")
    val award=awards[index]
    Column(Modifier.fillMaxWidth().testTag("achievement-celebration").clickable(onClick=dismiss),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("★",Modifier.scale(scale),style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.primary)
        Text("${awards.size} achievement${if(awards.size==1) "" else "s"} earned",style=MaterialTheme.typography.titleLarge)
        Text(award.name,style=MaterialTheme.typography.headlineMedium)
        Text(award.detail)
        Text("${index+1} of ${awards.size} · Based on runs saved on this device")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={index--},enabled=index>0) { Text("Previous") }
            OutlinedButton(onClick={index++},enabled=index<awards.lastIndex) { Text("Next") }
        }
        Button(onClick=dismiss,modifier=Modifier.fillMaxWidth()) { Text("Continue to summary") }
        Text("Coaching audio continues when you dismiss this.",style=MaterialTheme.typography.bodySmall)
    }
}
