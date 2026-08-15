package com.phoneaway.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreEngineTest {

    private fun input(
        mode: AwayMode = AwayMode.HONOR,
        minutes: Long,
        outcome: SessionOutcome = SessionOutcome.COMPLETED,
        streak: Int = 0,
    ) = ScoreInput(mode, minutes * 60, outcome, streak)

    @Test
    fun `ten minutes honor mode pays base rate`() {
        // 10 min * 10 pts, no bonus tier below 15 min, no streak.
        assertEquals(100, ScoreEngine.score(input(minutes = 10)).totalPoints)
    }

    @Test
    fun `sessions under a minute score nothing`() {
        val result = ScoreEngine.score(input(minutes = 0))
        assertEquals(0, result.totalPoints)
        assertEquals(0, result.basePoints)
    }

    @Test
    fun `harder modes pay strictly more for identical time`() {
        val points = AwayMode.entries.map { mode ->
            ScoreEngine.score(input(mode = mode, minutes = 30)).totalPoints
        }
        assertEquals(points.sortedBy { it }, points, "points must rise with mode difficulty")
        assertTrue(points.distinct().size == points.size, "no two modes should pay the same")
    }

    @Test
    fun `stillness mode pays 2_5x honor mode`() {
        val honor = ScoreEngine.score(input(mode = AwayMode.HONOR, minutes = 30)).totalPoints
        val still = ScoreEngine.score(input(mode = AwayMode.STILLNESS, minutes = 30)).totalPoints
        assertEquals(honor * 2.5, still.toDouble(), 1.0)
    }

    @Test
    fun `duration tiers apply at their thresholds`() {
        assertEquals(1.0, ScoreEngine.durationMultiplier(14 * 60))
        assertEquals(1.1, ScoreEngine.durationMultiplier(15 * 60))
        assertEquals(1.25, ScoreEngine.durationMultiplier(30 * 60))
        assertEquals(1.5, ScoreEngine.durationMultiplier(60 * 60))
        assertEquals(1.8, ScoreEngine.durationMultiplier(120 * 60))
        assertEquals(2.0, ScoreEngine.durationMultiplier(240 * 60))
    }

    @Test
    fun `one long session beats the same time split up`() {
        val single = ScoreEngine.score(input(minutes = 60)).totalPoints
        val split = (1..4).sumOf { ScoreEngine.score(input(minutes = 15)).totalPoints }
        assertTrue(single > split, "expected $single > $split")
    }

    @Test
    fun `streak bonus grows then caps`() {
        assertEquals(1.0, ScoreEngine.streakMultiplier(0))
        assertEquals(1.0, ScoreEngine.streakMultiplier(1))
        assertEquals(1.05, ScoreEngine.streakMultiplier(2), 1e-9)
        assertEquals(1.5, ScoreEngine.streakMultiplier(ScoreEngine.STREAK_BONUS_CAP_DAYS), 1e-9)
        assertEquals(1.5, ScoreEngine.streakMultiplier(500), 1e-9)
    }

    @Test
    fun `broken session gets partial credit and no bonuses`() {
        val broken = ScoreEngine.score(
            input(mode = AwayMode.STILLNESS, minutes = 60, outcome = SessionOutcome.BROKEN, streak = 9),
        )
        // 60 min * 10 * 2.5 mode * 0.25 credit, with duration and streak bonuses withheld.
        assertEquals(375, broken.totalPoints)
        assertEquals(1.0, broken.durationMultiplier)
        assertEquals(1.0, broken.streakMultiplier)
    }

    @Test
    fun `breaking a session always costs points versus finishing it`() {
        for (mode in AwayMode.entries) {
            val clean = ScoreEngine.score(input(mode = mode, minutes = 45)).totalPoints
            val broken = ScoreEngine.score(
                input(mode = mode, minutes = 45, outcome = SessionOutcome.BROKEN),
            ).totalPoints
            assertTrue(broken < clean, "$mode: broken $broken should be < clean $clean")
        }
    }

    @Test
    fun `projected points match a clean finish`() {
        val projected = ScoreEngine.projectedPoints(AwayMode.SCREEN_LOCKED, 45 * 60, streakDays = 3)
        val actual = ScoreEngine.score(
            input(mode = AwayMode.SCREEN_LOCKED, minutes = 45, streak = 3),
        ).totalPoints
        assertEquals(actual, projected)
    }

    @Test
    fun `points never decrease as a session runs longer`() {
        var previous = 0
        for (minute in 0..300) {
            val points = ScoreEngine.projectedPoints(AwayMode.STILLNESS, minute * 60L, streakDays = 5)
            assertTrue(points >= previous, "points dropped at minute $minute: $points < $previous")
            previous = points
        }
    }
}
