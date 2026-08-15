package com.phoneaway.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionRulesTest {

    private fun decide(
        mode: AwayMode,
        event: SessionEvent,
        phase: SessionPhase = SessionPhase.RUNNING,
    ) = SessionRules.decide(mode, phase, event)

    private fun finishedWith(reason: EndReason) = SessionDecision.Finish(reason)

    @Test
    fun `locked-screen modes arm until the screen goes off`() {
        assertEquals(SessionPhase.ARMING, SessionRules.initialPhase(AwayMode.SCREEN_LOCKED, screenIsOn = true))
        assertEquals(SessionPhase.ARMING, SessionRules.initialPhase(AwayMode.STILLNESS, screenIsOn = true))
    }

    @Test
    fun `modes that do not need a lock start running immediately`() {
        assertEquals(SessionPhase.RUNNING, SessionRules.initialPhase(AwayMode.HONOR, screenIsOn = true))
        assertEquals(SessionPhase.RUNNING, SessionRules.initialPhase(AwayMode.APP_OPEN, screenIsOn = true))
    }

    @Test
    fun `screen going off starts an arming session`() {
        assertEquals(
            SessionDecision.BeginRunning,
            decide(AwayMode.SCREEN_LOCKED, SessionEvent.ScreenOff, SessionPhase.ARMING),
        )
    }

    @Test
    fun `arming ignores everything except the screen going off`() {
        val ignored = listOf(
            SessionEvent.ScreenOn(keyguardLocked = false),
            SessionEvent.UserPresent,
            SessionEvent.DeviceMoved,
            SessionEvent.AppBackgrounded,
        )
        ignored.forEach { event ->
            assertEquals(
                SessionDecision.Ignore,
                decide(AwayMode.SCREEN_LOCKED, event, SessionPhase.ARMING),
                "arming should ignore $event",
            )
        }
    }

    @Test
    fun `honor mode is broken by nothing but the user`() {
        val events = listOf(
            SessionEvent.ScreenOn(keyguardLocked = false),
            SessionEvent.UserPresent,
            SessionEvent.DeviceMoved,
            SessionEvent.AppBackgrounded,
            SessionEvent.ScreenOff,
        )
        events.forEach { event ->
            assertEquals(SessionDecision.Ignore, decide(AwayMode.HONOR, event), "honor should ignore $event")
        }
        assertEquals(
            finishedWith(EndReason.USER_STOPPED),
            decide(AwayMode.HONOR, SessionEvent.UserStopped),
        )
    }

    @Test
    fun `app open mode breaks when the app is backgrounded`() {
        assertEquals(
            finishedWith(EndReason.APP_LEFT),
            decide(AwayMode.APP_OPEN, SessionEvent.AppBackgrounded),
        )
    }

    @Test
    fun `backgrounding the app is expected in every other mode`() {
        listOf(AwayMode.HONOR, AwayMode.SCREEN_LOCKED, AwayMode.STILLNESS).forEach { mode ->
            assertEquals(
                SessionDecision.Ignore,
                decide(mode, SessionEvent.AppBackgrounded),
                "$mode should survive backgrounding",
            )
        }
    }

    @Test
    fun `unlocking breaks the locked-screen modes`() {
        listOf(AwayMode.SCREEN_LOCKED, AwayMode.STILLNESS).forEach { mode ->
            assertEquals(
                finishedWith(EndReason.SCREEN_UNLOCKED),
                decide(mode, SessionEvent.UserPresent),
                "$mode should break on unlock",
            )
        }
    }

    @Test
    fun `peeking at a locked screen is allowed`() {
        assertEquals(
            SessionDecision.Ignore,
            decide(AwayMode.SCREEN_LOCKED, SessionEvent.ScreenOn(keyguardLocked = true)),
        )
    }

    @Test
    fun `a phone with no keyguard is open the moment the screen lights up`() {
        assertEquals(
            finishedWith(EndReason.SCREEN_UNLOCKED),
            decide(AwayMode.SCREEN_LOCKED, SessionEvent.ScreenOn(keyguardLocked = false)),
        )
    }

    @Test
    fun `only stillness mode cares about movement`() {
        assertEquals(
            finishedWith(EndReason.DEVICE_MOVED),
            decide(AwayMode.STILLNESS, SessionEvent.DeviceMoved),
        )
        listOf(AwayMode.HONOR, AwayMode.APP_OPEN, AwayMode.SCREEN_LOCKED).forEach { mode ->
            assertEquals(
                SessionDecision.Ignore,
                decide(mode, SessionEvent.DeviceMoved),
                "$mode should not care about movement",
            )
        }
    }

    @Test
    fun `hitting the target finishes any mode`() {
        AwayMode.entries.forEach { mode ->
            assertEquals(
                finishedWith(EndReason.TARGET_REACHED),
                decide(mode, SessionEvent.TargetReached),
                "$mode should finish on target",
            )
        }
    }

    @Test
    fun `an idle session ignores every event`() {
        AwayMode.entries.forEach { mode ->
            assertEquals(
                SessionDecision.Ignore,
                decide(mode, SessionEvent.UserPresent, SessionPhase.IDLE),
            )
        }
    }

    @Test
    fun `stopping is always honoured, in any mode or phase`() {
        for (mode in AwayMode.entries) {
            for (phase in listOf(SessionPhase.ARMING, SessionPhase.RUNNING)) {
                assertEquals(
                    finishedWith(EndReason.USER_STOPPED),
                    decide(mode, SessionEvent.UserStopped, phase),
                    "$mode in $phase should stop on request",
                )
            }
        }
    }
}
