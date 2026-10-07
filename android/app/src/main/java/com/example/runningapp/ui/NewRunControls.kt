package com.example.runningapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runningapp.domain.RunMode

@Composable
internal fun RunSetupChoices(
    options: List<Pair<String, String>>, selected: String, tag: String, onSelect: (String) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
    val emojiStyle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp)
    val labels = options.map { measurer.measure(it.first, labelStyle).size }
    val emojis = options.map { measurer.measure(it.second, emojiStyle).size }
    val minimumSide = with(density) {
        val horizontalRoom = 24.dp * fontScale
        maxOf(labels.maxOf { it.width }, emojis.maxOf { it.width },
            labels.maxOf { it.height } + emojis.maxOf { it.height } + 4.dp.roundToPx()).toDp() + horizontalRoom
    }.coerceAtLeast(48.dp)

    // Measure at the user's font scale before choosing columns; every tile stays square.
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup().testTag(tag)) {
        val gap = 8.dp
        val columns = ((maxWidth + gap) / (minimumSide + gap)).toInt().coerceIn(1, options.size)
        val side = ((maxWidth - gap * (columns - 1)) / columns)
            .coerceAtMost(maxOf(112.dp, minimumSide))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally) {
            options.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (label, emoji) ->
                        val chosen = selected == label
                        Surface(
                            modifier = Modifier.size(side).testTag("$tag-$label")
                                .selectable(chosen, role = Role.RadioButton, onClick = { onSelect(label) }),
                            shape = RoundedCornerShape(8.dp),
                            color = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(if (chosen) 2.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        ) {
                            Column(Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(emoji, Modifier.clearAndSetSemantics {}, style = emojiStyle)
                                Spacer(Modifier.height(4.dp))
                                Text(label, style = labelStyle, textAlign = TextAlign.Center,
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RunStatusIndicators(mode: RunMode, online: Boolean, dark: Boolean) {
    val green = if (dark) Color(0xFF82D998) else Color(0xFF176B36)
    val blue = if (dark) Color(0xFF91C7FF) else Color(0xFF175DA8)
    val red = if (dark) Color(0xFFFFAAA5) else Color(0xFFB3261E)
    Row(Modifier.fillMaxWidth().testTag("run-status"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (mode == RunMode.OUTDOOR) "Outdoor" else "Indoor",
            Modifier.weight(1f).testTag("run-type-status"),
            color = if (mode == RunMode.OUTDOOR) green else blue, fontWeight = FontWeight.SemiBold)
        Text(if (online) "Online" else "Fallback",
            Modifier.weight(1f).testTag("connectivity-status"), textAlign = TextAlign.End,
            color = if (online) green else red, fontWeight = FontWeight.SemiBold)
    }
}
