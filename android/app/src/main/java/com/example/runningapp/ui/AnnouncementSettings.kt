package com.example.runningapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.runningapp.domain.AnnouncementInterval
import com.example.runningapp.domain.AnnouncementSelection

@Composable
internal fun AnnouncementSettings(
    enabled: Boolean, selected: AnnouncementSelection, kilometers: Boolean,
    onEnabled: (Boolean) -> Unit, onSelection: (AnnouncementSelection) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Run announcements", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(enabled, onEnabled, Modifier.testTag("announcements-enabled"))
        }
        for (time in listOf(true, false)) {
            val channelEnabled = if (time) selected.timeEnabled else selected.distanceEnabled
            val current = if (time) selected.timeInterval else selected.distanceInterval
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (time) "Time milestones" else "Distance milestones", Modifier.weight(1f))
                Switch(channelEnabled, { value -> onSelection(if (time) selected.copy(timeEnabled = value)
                    else selected.copy(distanceEnabled = value)) },
                    Modifier.testTag(if (time) "announcement-time-enabled" else "announcement-distance-enabled"),
                    enabled = enabled)
            }
            Column(Modifier.selectableGroup()) {
                AnnouncementInterval.entries.filter { (it.timeMs != null) == time }.forEach { interval ->
                    val label = when (interval) {
                        AnnouncementInterval.FIVE_MINUTES -> "5 minutes"
                        AnnouncementInterval.TEN_MINUTES -> "10 minutes"
                        AnnouncementInterval.HALF_UNIT -> if (kilometers) "0.5 kilometer" else "0.5 mile"
                        AnnouncementInterval.ONE_UNIT -> if (kilometers) "1 kilometer" else "1 mile"
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("announcement-${interval.name}")
                        .selectable(current == interval, enabled && channelEnabled, Role.RadioButton) {
                            onSelection(if (time) selected.copy(timeInterval = interval) else selected.copy(distanceInterval = interval))
                        }
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(current == interval, onClick = null, enabled = enabled && channelEnabled)
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = if (enabled && channelEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            }
        }
    }
}
