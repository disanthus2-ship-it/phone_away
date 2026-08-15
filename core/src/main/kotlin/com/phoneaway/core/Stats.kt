package com.phoneaway.core

import java.time.LocalDate
import java.time.ZoneId

data class Stats(
    val totalPoints: Int,
    val level: LevelState,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalSessions: Int,
    val completedSessions: Int,
    val brokenSessions: Int,
    val totalFocusedSeconds: Long,
    val bestSessionSeconds: Long,
    val secondsToday: Long,
    val pointsToday: Int,
    val favouriteMode: AwayMode?,
) {
    val successRate: Float
        get() = if (totalSessions == 0) 0f else completedSessions.toFloat() / totalSessions
}

object StatsCalculator {

    fun qualifyingDates(sessions: List<Session>, zone: ZoneId): Set<LocalDate> =
        sessions.filter(StreakCalculator::qualifies).map { it.dateIn(zone) }.toSet()

    fun compute(sessions: List<Session>, zone: ZoneId, today: LocalDate): Stats {
        val totalPoints = sessions.sumOf { it.pointsAwarded }
        val dates = qualifyingDates(sessions, zone)
        val todaysSessions = sessions.filter { it.dateIn(zone) == today }

        return Stats(
            totalPoints = totalPoints,
            level = LevelSystem.stateFor(totalPoints),
            currentStreak = StreakCalculator.currentStreak(dates, today),
            longestStreak = StreakCalculator.longestStreak(dates),
            totalSessions = sessions.size,
            completedSessions = sessions.count { it.outcome == SessionOutcome.COMPLETED },
            brokenSessions = sessions.count { it.outcome == SessionOutcome.BROKEN },
            totalFocusedSeconds = sessions.sumOf { it.focusedSeconds },
            bestSessionSeconds = sessions.maxOfOrNull { it.focusedSeconds } ?: 0L,
            secondsToday = todaysSessions.sumOf { it.focusedSeconds },
            pointsToday = todaysSessions.sumOf { it.pointsAwarded },
            favouriteMode = sessions.groupingBy { it.mode }.eachCount()
                .maxByOrNull { it.value }?.key,
        )
    }
}
