package com.phoneaway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneaway.core.AwayMode
import com.phoneaway.core.ScoreEngine
import com.phoneaway.core.Stats

/** Preset session lengths, plus open-ended. */
val durationPresets: List<Long?> = listOf(15L, 30L, 60L, 120L, null)

fun durationLabel(minutes: Long?): String = when {
    minutes == null -> "Open"
    minutes >= 60 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

@Composable
fun HomeScreen(
    stats: Stats,
    selectedMode: AwayMode,
    selectedMinutes: Long?,
    onModeChange: (AwayMode) -> Unit,
    onMinutesChange: (Long?) -> Unit,
    onStart: () -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LevelHeader(stats = stats, onShowHistory = onShowHistory) }

        item {
            Text(
                text = "How strict?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        items(AwayMode.entries) { mode ->
            ModeCard(
                mode = mode,
                selected = mode == selectedMode,
                onClick = { onModeChange(mode) },
            )
        }

        item {
            Text(
                text = "How long?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durationPresets.forEach { minutes ->
                    FilterChip(
                        selected = minutes == selectedMinutes,
                        onClick = { onMinutesChange(minutes) },
                        label = { Text(durationLabel(minutes)) },
                    )
                }
            }
        }

        item {
            RewardPreview(
                mode = selectedMode,
                minutes = selectedMinutes,
                streak = stats.streakForScoring,
            )
        }

        item {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Put the phone away", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LevelHeader(stats: Stats, onShowHistory: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowHistory),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Level ${stats.level.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stats.level.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stats.totalPoints}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "points",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { stats.level.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )

            Spacer(Modifier.height(8.dp))

            val toNext = stats.level.pointsForLevel?.let { it - stats.level.pointsIntoLevel }
            Text(
                text = buildString {
                    append(if (toNext != null) "$toNext pts to level ${stats.level.level + 1}" else "Max level")
                    if (stats.currentStreak > 0) append("  ·  ${stats.currentStreak}-day streak")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ModeCard(mode: AwayMode, selected: Boolean, onClick: () -> Unit) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "×${mode.multiplier}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = mode.tagline, style = MaterialTheme.typography.bodyMedium)
            if (selected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ends on: ${mode.breaksOn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shows what the chosen combination is worth before committing to it. */
@Composable
private fun RewardPreview(mode: AwayMode, minutes: Long?, streak: Int) {
    val previewMinutes = minutes ?: 30L
    val points = ScoreEngine.projectedPoints(
        mode = mode,
        focusedSeconds = previewMinutes * 60,
        streakDays = streak,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (minutes == null) "A 30-minute run would earn" else "This run earns",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$points points",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (streak > 1) {
                Text(
                    text = "Includes your ${streak}-day streak bonus " +
                        "(×${"%.2f".format(ScoreEngine.streakMultiplier(streak))})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
