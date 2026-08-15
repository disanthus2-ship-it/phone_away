package com.phoneaway.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 8, 15)
    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    private fun session(
        outcome: SessionOutcome = SessionOutcome.COMPLETED,
        seconds: Long = 15 * 60,
    ) = Session(
        id = "s",
        mode = AwayMode.HONOR,
        startedAt = Instant.EPOCH,
        endedAt = Instant.EPOCH.plusSeconds(seconds),
        focusedSeconds = seconds,
        targetSeconds = null,
        outcome = outcome,
        endReason = EndReason.USER_STOPPED,
        pointsAwarded = 0,
    )

    @Test
    fun `no history means no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptySet(), today))
    }

    @Test
    fun `consecutive days count up`() {
        assertEquals(3, StreakCalculator.currentStreak(days(0, 1, 2), today))
    }

    @Test
    fun `a gap ends the streak`() {
        assertEquals(1, StreakCalculator.currentStreak(days(0, 2, 3), today))
    }

    @Test
    fun `today being unearned does not break a streak yet`() {
        // Earned yesterday and the day before, nothing yet today -- still a streak of 2.
        assertEquals(2, StreakCalculator.currentStreak(days(1, 2), today))
    }

    @Test
    fun `missing yesterday and today ends the streak`() {
        assertEquals(0, StreakCalculator.currentStreak(days(2, 3, 4), today))
    }

    @Test
    fun `scoring streak counts today optimistically`() {
        // Two days behind you; the session being scored is today's qualifier, so it pays at 3.
        assertEquals(3, StreakCalculator.streakForScoring(days(1, 2), today))
    }

    @Test
    fun `longest streak finds the best historical run`() {
        val dates = days(0, 1, 5, 6, 7, 8, 12)
        assertEquals(4, StreakCalculator.longestStreak(dates))
    }

    @Test
    fun `only clean sessions of sufficient length qualify`() {
        assertTrue(StreakCalculator.qualifies(session()))
        assertFalse(StreakCalculator.qualifies(session(outcome = SessionOutcome.BROKEN)))
        assertFalse(StreakCalculator.qualifies(session(seconds = 5 * 60)))
    }
}
