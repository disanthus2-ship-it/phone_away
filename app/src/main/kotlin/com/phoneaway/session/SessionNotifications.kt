package com.phoneaway.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.phoneaway.R
import com.phoneaway.core.Session
import com.phoneaway.core.SessionOutcome
import com.phoneaway.core.SessionPhase
import com.phoneaway.ui.MainActivity

/** Builds the ongoing-session notification and the one shown when a run ends. */
class SessionNotifications(private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.channel_ongoing),
                // Low importance: a silent, persistent status line. Buzzing the
                // phone during a screen-free session would defeat the point.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_ongoing_description)
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.channel_result),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_result_description)
            },
        )
    }

    fun ongoing(state: ActiveSessionState): Notification {
        val title = when (state.phase) {
            SessionPhase.ARMING -> context.getString(R.string.notification_arming_title)
            else -> context.getString(R.string.notification_running_title, state.mode.displayName)
        }

        val text = when (state.phase) {
            SessionPhase.ARMING -> context.getString(R.string.notification_arming_text)
            else -> {
                val elapsed = formatDuration(state.focusedSeconds)
                val remaining = state.remainingSeconds
                if (remaining != null) {
                    context.getString(
                        R.string.notification_running_text_target,
                        elapsed,
                        formatDuration(remaining),
                        state.projectedPoints,
                    )
                } else {
                    context.getString(R.string.notification_running_text, elapsed, state.projectedPoints)
                }
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_end_session),
                stopIntent(),
            )
            .build()
    }

    fun result(session: Session): Notification {
        val title = when (session.outcome) {
            SessionOutcome.COMPLETED -> context.getString(R.string.result_title_completed)
            SessionOutcome.BROKEN -> context.getString(R.string.result_title_broken)
            SessionOutcome.ABANDONED -> context.getString(R.string.result_title_abandoned)
        }

        return NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                context.getString(
                    R.string.result_text,
                    formatDuration(session.focusedSeconds),
                    session.pointsAwarded,
                ),
            )
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, SessionActionReceiver::class.java).setAction(SessionActionReceiver.ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ONGOING = "session_ongoing"
        const val CHANNEL_RESULT = "session_result"
        const val ONGOING_ID = 1001
        const val RESULT_ID = 1002

        fun formatDuration(totalSeconds: Long): String {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
    }
}
