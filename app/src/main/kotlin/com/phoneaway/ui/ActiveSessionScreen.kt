package com.phoneaway.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneaway.core.AwayMode
import com.phoneaway.core.ScoreEngine
import com.phoneaway.core.SessionPhase
import com.phoneaway.session.ActiveSessionState
import com.phoneaway.session.SessionNotifications

@Composable
fun ActiveSessionScreen(
    state: ActiveSessionState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.mode.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        if (state.phase == SessionPhase.ARMING) {
            ArmingContent(state.mode)
        } else {
            RunningContent(state)
        }

        Spacer(Modifier.height(48.dp))

        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.phase == SessionPhase.ARMING) "Cancel" else "End session")
        }

        if (state.phase == SessionPhase.RUNNING && state.mode != AwayMode.HONOR) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Ending early keeps only ${(ScoreEngine.PARTIAL_CREDIT_RATE * 100).toInt()}% " +
                    "of the base points.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ArmingContent(mode: AwayMode) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(120.dp), strokeWidth = 6.dp)
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Lock your phone to start",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "The clock starts the moment the screen goes off, so nothing is " +
            "counted while you are still looking at it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (mode.requiresStillness) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Then set it down — picking it up ends the run.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RunningContent(state: ActiveSessionState) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        val progress = state.progress
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = SessionNotifications.formatDuration(state.focusedSeconds),
                fontSize = 44.sp,
                style = MaterialTheme.typography.displaySmall,
            )
            state.remainingSeconds?.let { remaining ->
                Text(
                    text = "${SessionNotifications.formatDuration(remaining)} left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = "${state.projectedPoints} points",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "banked so far",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
