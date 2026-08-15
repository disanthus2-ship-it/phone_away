package com.phoneaway.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end runs through the pieces the Android service wires together:
 * [SessionRules] decides, [SessionFactory] records, [ScoreEngine] pays.
 *
 * The simulator below stands in for the service's clock and event sources, so
 * whole user journeys can be checked without a device.
 */
class SessionSimulationTest {

    /** Replays a timeline of events against the rules, tracking elapsed time. */
    private class Simulator(
        val mode: AwayMode,
        val targetSeconds: Long? = null,
        val streakDays: Int = 0,
        screenIsOn: Boolean = true,
    ) {
        var phase: SessionPhase = SessionRules.initialPhase(mode, screenIsOn)
        private var clockStartedAt: Long? = if (phase == SessionPhase.RUNNING) 0L else null
        private var now: Long = 0L
        var result: Session? = null

        fun elapsed(): Long = clockStartedAt?.let { now - it } ?: 0L

        fun advance(seconds: Long) = apply {
            var remaining = seconds
            // Step second by second so a target can trigger mid-advance, the
            // same way the service's ticker would notice it.
            while (remaining > 0 && result == null) {
                now++
                remaining--
                val target = targetSeconds
                if (phase == SessionPhase.RUNNING && target != null && elapsed() >= target) {
                    send(SessionEvent.TargetReached)
                }
            }
        }

        fun send(event: SessionEvent) = apply {
            if (result != null) return@apply
            when (val decision = SessionRules.decide(mode, phase, event)) {
                is SessionDecision.Ignore -> Unit
                is SessionDecision.BeginRunning -> {
                    phase = SessionPhase.RUNNING
                    clockStartedAt = now
                }
                is SessionDecision.Finish -> {
                    val focused = elapsed()
                    phase = SessionPhase.IDLE
                    result = SessionFactory.build(
                        id = "sim",
                        mode = mode,
                        startedAt = Instant.EPOCH,
                        endedAt = Instant.EPOCH.plusSeconds(now),
                        focusedSeconds = focused,
                        targetSeconds = targetSeconds,
                        endReason = decision.reason,
                        streakDays = streakDays,
                    )
                }
            }
        }

        fun require(): Session = requireNotNull(result) { "session never finished" }
    }

    @Test
    fun `a clean locked-screen run pays full points`() {
        val session = Simulator(AwayMode.SCREEN_LOCKED, targetSeconds = 30 * 60)
            .send(SessionEvent.ScreenOff)
            .advance(30 * 60)
            .require()

        assertEquals(SessionOutcome.COMPLETED, session.outcome)
        assertEquals(EndReason.TARGET_REACHED, session.endReason)
        assertEquals(30 * 60, session.focusedSeconds)
        // 30 min * 10 * 1.75 mode * 1.25 duration tier
        assertEquals(656, session.pointsAwarded)
    }

    @Test
    fun `time before the phone is locked is not counted`() {
        val session = Simulator(AwayMode.SCREEN_LOCKED)
            .advance(10 * 60) // fiddling with the app, screen still on
            .send(SessionEvent.ScreenOff)
            .advance(20 * 60)
            .send(SessionEvent.UserPresent)
            .require()

        assertEquals(20 * 60, session.focusedSeconds, "only post-lock time should count")
    }

    @Test
    fun `peeking at the clock does not cost the session`() {
        val session = Simulator(AwayMode.SCREEN_LOCKED)
            .send(SessionEvent.ScreenOff)
            .advance(20 * 60)
            .send(SessionEvent.ScreenOn(keyguardLocked = true))
            .advance(60)
            .send(SessionEvent.ScreenOff)
            .advance(20 * 60)
            .send(SessionEvent.UserStopped)
            .require()

        assertEquals(SessionOutcome.COMPLETED, session.outcome)
        assertEquals(41 * 60, session.focusedSeconds)
    }

    @Test
    fun `unlocking mid-run breaks it and forfeits the bonuses`() {
        val session = Simulator(AwayMode.STILLNESS, targetSeconds = 2 * 60 * 60, streakDays = 5)
            .send(SessionEvent.ScreenOff)
            .advance(90 * 60)
            .send(SessionEvent.UserPresent)
            .require()

        assertEquals(SessionOutcome.BROKEN, session.outcome)
        assertEquals(EndReason.SCREEN_UNLOCKED, session.endReason)
        // 90 min * 10 * 2.5 mode * 0.25 credit, no duration or streak bonus.
        assertEquals(563, session.pointsAwarded)
    }

    @Test
    fun `picking up the phone ends a hands-off run only`() {
        val stillness = Simulator(AwayMode.STILLNESS)
            .send(SessionEvent.ScreenOff)
            .advance(15 * 60)
            .send(SessionEvent.DeviceMoved)
            .require()
        assertEquals(EndReason.DEVICE_MOVED, stillness.endReason)

        val locked = Simulator(AwayMode.SCREEN_LOCKED)
            .send(SessionEvent.ScreenOff)
            .advance(15 * 60)
            .send(SessionEvent.DeviceMoved)
        assertEquals(null, locked.result, "moving a locked-screen session is fine")
    }

    @Test
    fun `an app-open run survives the screen but not the home button`() {
        val session = Simulator(AwayMode.APP_OPEN)
            .advance(25 * 60)
            .send(SessionEvent.AppBackgrounded)
            .require()

        assertEquals(SessionOutcome.BROKEN, session.outcome)
        assertEquals(EndReason.APP_LEFT, session.endReason)
        assertEquals(25 * 60, session.focusedSeconds)
    }

    @Test
    fun `stopping an open-ended run is a success, stopping a targeted one is not`() {
        val openEnded = Simulator(AwayMode.HONOR)
            .advance(40 * 60)
            .send(SessionEvent.UserStopped)
            .require()
        assertEquals(SessionOutcome.COMPLETED, openEnded.outcome)

        val targeted = Simulator(AwayMode.HONOR, targetSeconds = 60 * 60)
            .advance(40 * 60)
            .send(SessionEvent.UserStopped)
            .require()
        assertEquals(SessionOutcome.ABANDONED, targeted.outcome)
        assertTrue(targeted.pointsAwarded < openEnded.pointsAwarded)
    }

    @Test
    fun `a session started with the screen already off runs immediately`() {
        val session = Simulator(AwayMode.SCREEN_LOCKED, screenIsOn = false)
            .advance(12 * 60)
            .send(SessionEvent.UserStopped)
            .require()

        assertEquals(12 * 60, session.focusedSeconds)
    }

    @Test
    fun `a streak makes the same run worth more`() {
        fun runWithStreak(streak: Int) = Simulator(AwayMode.SCREEN_LOCKED, streakDays = streak)
            .send(SessionEvent.ScreenOff)
            .advance(60 * 60)
            .send(SessionEvent.UserStopped)
            .require()
            .pointsAwarded

        assertTrue(runWithStreak(7) > runWithStreak(1))
        assertEquals(runWithStreak(11), runWithStreak(30), "streak bonus is capped")
    }

    @Test
    fun `a full week of daily sessions levels you up`() {
        var total = 0
        repeat(7) {
            total += Simulator(AwayMode.SCREEN_LOCKED, streakDays = it + 1)
                .send(SessionEvent.ScreenOff)
                .advance(45 * 60)
                .send(SessionEvent.UserStopped)
                .require()
                .pointsAwarded
        }

        val level = LevelSystem.stateFor(total)
        assertTrue(level.level >= 3, "a week of 45-minute sessions should reach level 3+, got $level")
    }
}
