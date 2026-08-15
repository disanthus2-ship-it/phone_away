package com.phoneaway.core

import java.time.LocalDate

/**
 * Daily streaks.
 *
 * A day counts only if it contains at least one *completed* session of at least
 * [QUALIFYING_SECONDS]. Broken sessions never count -- but the streak survives
 * until the day itself is over, so a failed morning can still be rescued.
 */
object StreakCalculator {

    /** A day needs this much clean time away to keep the streak alive. */
    const val QUALIFYING_SECONDS = 10 * 60L

    fun qualifies(session: Session): Boolean =
        session.outcome == SessionOutcome.COMPLETED && session.focusedSeconds >= QUALIFYING_SECONDS

    /**
     * Current streak length in days, counting back from [today].
     *
     * Today not being earned yet does not break the streak -- the day isn't over.
     * In that case the streak is whatever it was through yesterday.
     */
    fun currentStreak(qualifyingDates: Set<LocalDate>, today: LocalDate): Int {
        if (qualifyingDates.isEmpty()) return 0

        var cursor = if (qualifyingDates.contains(today)) today else today.minusDays(1)
        var streak = 0
        while (qualifyingDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Longest streak ever recorded, for the stats screen. */
    fun longestStreak(qualifyingDates: Set<LocalDate>): Int {
        if (qualifyingDates.isEmpty()) return 0

        val sorted = qualifyingDates.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i - 1].plusDays(1) == sorted[i]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * The streak value to score a session with: today already counted, so a
     * session that pushes you over today's threshold is paid at the new streak.
     */
    fun streakForScoring(qualifyingDates: Set<LocalDate>, today: LocalDate): Int =
        currentStreak(qualifyingDates + today, today)
}
