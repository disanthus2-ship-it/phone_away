package com.phoneaway.data

import com.phoneaway.core.AwayMode
import com.phoneaway.core.EndReason
import com.phoneaway.core.Session
import com.phoneaway.core.SessionOutcome
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * On-disk shape of a [Session]. Enums are stored as strings so that adding a
 * mode later never makes old history unreadable.
 */
@Serializable
data class StoredSession(
    val id: String,
    val mode: String,
    val startedAtEpochSeconds: Long,
    val endedAtEpochSeconds: Long,
    val focusedSeconds: Long,
    val targetSeconds: Long? = null,
    val outcome: String,
    val endReason: String,
    val pointsAwarded: Int,
) {
    fun toSession(): Session = Session(
        id = id,
        mode = AwayMode.fromId(mode),
        startedAt = Instant.ofEpochSecond(startedAtEpochSeconds),
        endedAt = Instant.ofEpochSecond(endedAtEpochSeconds),
        focusedSeconds = focusedSeconds,
        targetSeconds = targetSeconds,
        outcome = runCatching { SessionOutcome.valueOf(outcome) }.getOrDefault(SessionOutcome.COMPLETED),
        endReason = runCatching { EndReason.valueOf(endReason) }.getOrDefault(EndReason.USER_STOPPED),
        pointsAwarded = pointsAwarded,
    )

    companion object {
        fun from(session: Session): StoredSession = StoredSession(
            id = session.id,
            mode = session.mode.id,
            startedAtEpochSeconds = session.startedAt.epochSecond,
            endedAtEpochSeconds = session.endedAt.epochSecond,
            focusedSeconds = session.focusedSeconds,
            targetSeconds = session.targetSeconds,
            outcome = session.outcome.name,
            endReason = session.endReason.name,
            pointsAwarded = session.pointsAwarded,
        )
    }
}
