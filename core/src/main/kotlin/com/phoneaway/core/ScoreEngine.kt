package com.phoneaway.core

import kotlin.math.min
import kotlin.math.roundToInt

/** Everything that goes into scoring one session. */
data class ScoreInput(
    val mode: AwayMode,
    val focusedSeconds: Long,
    val outcome: SessionOutcome,
    /** Current daily streak *including* today, as it stands before this session is filed. */
    val streakDays: Int = 0,
)

/** A transparent breakdown, so the result screen can show its work. */
data class ScoreBreakdown(
    val basePoints: Int,
    val modeMultiplier: Double,
    val durationMultiplier: Double,
    val streakMultiplier: Double,
    /** 1.0 for a clean session; a fraction when credit is partial. */
    val creditRate: Double,
    val totalPoints: Int,
)

/**
 * Turns time-away into points.
 *
 * The shape of the curve is deliberate:
 *  - Points are linear in time, so ten minutes is always worth ten minutes.
 *  - Longer *unbroken* stretches earn a multiplier, because the fifth
 *    uninterrupted hour is much harder than the first ten minutes.
 *  - Harder-to-cheat modes pay more (see [AwayMode.multiplier]).
 *  - Streaks add a modest, capped bonus -- enough to protect a habit, not so
 *    much that breaking one feels catastrophic.
 *  - A broken session still pays a little, so a bad run doesn't feel wasted.
 */
object ScoreEngine {

    const val POINTS_PER_MINUTE = 10.0

    /** Sessions shorter than this score nothing at all. */
    const val MIN_SCORING_SECONDS = 60L

    /** Fraction of base points kept when a session is broken or abandoned. */
    const val PARTIAL_CREDIT_RATE = 0.25

    /** Cap on the streak bonus, reached after [STREAK_BONUS_CAP_DAYS] days. */
    const val MAX_STREAK_MULTIPLIER = 1.5
    const val STREAK_BONUS_PER_DAY = 0.05
    const val STREAK_BONUS_CAP_DAYS = 11

    /** Minute thresholds and the multiplier they unlock, longest first. */
    private val durationTiers = listOf(
        240 to 2.0,
        120 to 1.8,
        60 to 1.5,
        30 to 1.25,
        15 to 1.1,
    )

    fun durationMultiplier(focusedSeconds: Long): Double {
        val minutes = focusedSeconds / 60.0
        return durationTiers.firstOrNull { (threshold, _) -> minutes >= threshold }?.second ?: 1.0
    }

    fun streakMultiplier(streakDays: Int): Double {
        if (streakDays <= 1) return 1.0
        val bonus = STREAK_BONUS_PER_DAY * (streakDays - 1)
        return min(1.0 + bonus, MAX_STREAK_MULTIPLIER)
    }

    fun score(input: ScoreInput): ScoreBreakdown {
        val clean = input.outcome == SessionOutcome.COMPLETED
        val creditRate = if (clean) 1.0 else PARTIAL_CREDIT_RATE

        if (input.focusedSeconds < MIN_SCORING_SECONDS) {
            return ScoreBreakdown(
                basePoints = 0,
                modeMultiplier = input.mode.multiplier,
                durationMultiplier = 1.0,
                streakMultiplier = 1.0,
                creditRate = creditRate,
                totalPoints = 0,
            )
        }

        val base = (input.focusedSeconds / 60.0) * POINTS_PER_MINUTE

        // Bonus multipliers reward sustained, honest runs, so a broken session
        // gets the flat rate only -- no duration or streak bonus on top.
        val duration = if (clean) durationMultiplier(input.focusedSeconds) else 1.0
        val streak = if (clean) streakMultiplier(input.streakDays) else 1.0

        val total = base * input.mode.multiplier * duration * streak * creditRate

        return ScoreBreakdown(
            basePoints = base.roundToInt(),
            modeMultiplier = input.mode.multiplier,
            durationMultiplier = duration,
            streakMultiplier = streak,
            creditRate = creditRate,
            totalPoints = total.roundToInt(),
        )
    }

    /**
     * Points a session is worth *right now*, for the live counter during a run.
     * Optimistic: it assumes the session will end cleanly.
     */
    fun projectedPoints(mode: AwayMode, focusedSeconds: Long, streakDays: Int): Int =
        score(
            ScoreInput(
                mode = mode,
                focusedSeconds = focusedSeconds,
                outcome = SessionOutcome.COMPLETED,
                streakDays = streakDays,
            ),
        ).totalPoints
}
