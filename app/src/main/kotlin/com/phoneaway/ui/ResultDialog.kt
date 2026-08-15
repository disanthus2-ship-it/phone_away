package com.phoneaway.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneaway.core.ScoreEngine
import com.phoneaway.core.Session
import com.phoneaway.core.SessionOutcome
import com.phoneaway.session.SessionNotifications

@Composable
fun ResultDialog(session: Session, onDismiss: () -> Unit) {
    val title = when (session.outcome) {
        SessionOutcome.COMPLETED -> "Nice work"
        SessionOutcome.BROKEN -> session.endReason.display
        SessionOutcome.ABANDONED -> "Ended early"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "+${session.pointsAwarded} points",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "${SessionNotifications.formatDuration(session.focusedSeconds)} away " +
                        "on ${session.mode.displayName} (×${session.mode.multiplier})",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (session.outcome != SessionOutcome.COMPLETED) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Unfinished runs keep " +
                            "${(ScoreEngine.PARTIAL_CREDIT_RATE * 100).toInt()}% of base points " +
                            "and no bonuses. The time still counted for something.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
