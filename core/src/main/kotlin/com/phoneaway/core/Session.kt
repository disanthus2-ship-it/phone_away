package com.phoneaway.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How a session ended. */
enum class SessionOutcome {
    /** Reached its target, or was an open-ended run the user ended deliberately. */
    COMPLETED,

    /** The mode's rule was violated -- the phone was picked up, unlocked, etc. */
    BROKEN,

    /** The user stopped a targeted session before reaching the target. */
    ABANDONED,
}

/** Why a session stopped. Kept separate from the outcome so the UI can explain itself. */
enum class EndReason(val display: String) {
    TARGET_REACHED("You made it"),
    USER_STOPPED("You ended the session"),
    SCREEN_UNLOCKED("The phone was unlocked"),
    APP_LEFT("You left the app"),
    DEVICE_MOVED("The phone was picked up"),
    ;

    /**
     * Ending an open-ended session on purpose is a success, not a failure --
     * only walking out on a target you set yourself counts as abandoning it.
     */
    fun toOutcome(hadTarget: Boolean, reachedTarget: Boolean): SessionOutcome = when (this) {
        TARGET_REACHED -> SessionOutcome.COMPLETED
        USER_STOPPED ->
            if (!hadTarget || reachedTarget) SessionOutcome.COMPLETED else SessionOutcome.ABANDONED
        SCREEN_UNLOCKED, APP_LEFT, DEVICE_MOVED -> SessionOutcome.BROKEN
    }
}

/** One finished attempt at putting the phone away. */
data class Session(
    val id: String,
    val mode: AwayMode,
    val startedAt: Instant,
    val endedAt: Instant,
    /** Seconds actually spent away. Excludes any time before the phone was locked. */
    val focusedSeconds: Long,
    /** Null for an open-ended session. */
    val targetSeconds: Long?,
    val outcome: SessionOutcome,
    val endReason: EndReason,
    val pointsAwarded: Int,
) {
    val focusedMinutes: Double get() = focusedSeconds / 60.0

    fun dateIn(zone: ZoneId): LocalDate = startedAt.atZone(zone).toLocalDate()
}
