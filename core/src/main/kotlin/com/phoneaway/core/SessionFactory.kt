package com.phoneaway.core

import java.time.Instant

/**
 * Turns a finished run into the record that gets stored and scored.
 *
 * This is the moment the user's time becomes points, so it lives here rather
 * than inside the Android service where it could only be checked by hand.
 */
object SessionFactory {

    fun build(
        id: String,
        mode: AwayMode,
        startedAt: Instant,
        endedAt: Instant,
        focusedSeconds: Long,
        targetSeconds: Long?,
        endReason: EndReason,
        streakDays: Int,
    ): Session {
        val reachedTarget = targetSeconds != null && focusedSeconds >= targetSeconds
        val outcome = endReason.toOutcome(
            hadTarget = targetSeconds != null,
            reachedTarget = reachedTarget,
        )

        val breakdown = ScoreEngine.score(
            ScoreInput(
                mode = mode,
                focusedSeconds = focusedSeconds,
                outcome = outcome,
                streakDays = streakDays,
            ),
        )

        return Session(
            id = id,
            mode = mode,
            startedAt = startedAt,
            endedAt = endedAt,
            focusedSeconds = focusedSeconds,
            targetSeconds = targetSeconds,
            outcome = outcome,
            endReason = endReason,
            pointsAwarded = breakdown.totalPoints,
        )
    }
}
