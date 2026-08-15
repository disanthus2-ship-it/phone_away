package com.phoneaway.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneaway.core.Session
import com.phoneaway.core.SessionOutcome
import com.phoneaway.core.Stats
import com.phoneaway.session.SessionNotifications
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

@Composable
fun HistoryScreen(
    stats: Stats,
    sessions: List<Session>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatsGrid(stats) }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "No sessions yet. Your first one is the hardest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        items(sessions, key = { it.id }) { session -> SessionRow(session) }
    }
}

@Composable
private fun StatsGrid(stats: Stats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                StatCell("Current streak", "${stats.currentStreak}d")
                StatCell("Best streak", "${stats.longestStreak}d")
                StatCell("Today", SessionNotifications.formatDuration(stats.secondsToday))
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                StatCell("Total away", SessionNotifications.formatDuration(stats.totalFocusedSeconds))
                StatCell("Longest run", SessionNotifications.formatDuration(stats.bestSessionSeconds))
                StatCell("Finished", "${(stats.successRate * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(session: Session) {
    val accent = when (session.outcome) {
        SessionOutcome.COMPLETED -> MaterialTheme.colorScheme.secondary
        SessionOutcome.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
        SessionOutcome.BROKEN -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "${SessionNotifications.formatDuration(session.focusedSeconds)} · " +
                        session.mode.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = session.startedAt.atZone(ZoneId.systemDefault()).format(dayFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = session.endReason.display,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
            Text(
                text = "+${session.pointsAwarded}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}
