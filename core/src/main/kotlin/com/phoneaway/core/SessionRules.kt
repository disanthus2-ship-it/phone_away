package com.phoneaway.core

enum class SessionPhase {
    /** Nothing running. */
    IDLE,

    /**
     * Started, but the clock has not begun. Modes that require a locked screen
     * wait here until the screen actually goes off -- otherwise you could earn
     * Hands Off points while staring at the phone.
     */
    ARMING,

    /** The clock is running. */
    RUNNING,
}

/** Everything the outside world can tell a running session. */
sealed interface SessionEvent {
    data object ScreenOff : SessionEvent

    /**
     * The display lit up. [keyguardLocked] distinguishes glancing at a locked
     * phone from the phone being genuinely open.
     */
    data class ScreenOn(val keyguardLocked: Boolean) : SessionEvent

    /** The keyguard was dismissed -- the phone is now unlocked and in use. */
    data object UserPresent : SessionEvent

    /** The app went to the background. */
    data object AppBackgrounded : SessionEvent

    /** The phone was picked up or carried. */
    data object DeviceMoved : SessionEvent

    /** The user ended it deliberately. */
    data object UserStopped : SessionEvent

    /** The target duration was reached. */
    data object TargetReached : SessionEvent
}

sealed interface SessionDecision {
    /** Nothing to do. */
    data object Ignore : SessionDecision

    /** Start the clock. */
    data object BeginRunning : SessionDecision

    /** End the session for the given reason. */
    data class Finish(val reason: EndReason) : SessionDecision
}

/**
 * The break rules, as a pure function of mode, phase, and event.
 *
 * Keeping this out of the Android service means the rules that decide whether
 * somebody keeps an hour of credit are testable on their own, without a device.
 */
object SessionRules {

    /** The phase a session begins in. */
    fun initialPhase(mode: AwayMode, screenIsOn: Boolean): SessionPhase =
        if (mode.requiresLockedScreen && screenIsOn) SessionPhase.ARMING else SessionPhase.RUNNING

    fun decide(mode: AwayMode, phase: SessionPhase, event: SessionEvent): SessionDecision {
        if (phase == SessionPhase.IDLE) return SessionDecision.Ignore

        // Stopping deliberately always works, in any phase or mode.
        if (event is SessionEvent.UserStopped) {
            return SessionDecision.Finish(EndReason.USER_STOPPED)
        }

        if (phase == SessionPhase.ARMING) {
            // While arming, the only thing that matters is the screen going off.
            return if (event is SessionEvent.ScreenOff && mode.requiresLockedScreen) {
                SessionDecision.BeginRunning
            } else {
                SessionDecision.Ignore
            }
        }

        return when (event) {
            is SessionEvent.TargetReached -> SessionDecision.Finish(EndReason.TARGET_REACHED)

            is SessionEvent.AppBackgrounded ->
                if (mode == AwayMode.APP_OPEN) {
                    SessionDecision.Finish(EndReason.APP_LEFT)
                } else {
                    // In every other mode, leaving the app is the whole point.
                    SessionDecision.Ignore
                }

            is SessionEvent.DeviceMoved ->
                if (mode.requiresStillness) {
                    SessionDecision.Finish(EndReason.DEVICE_MOVED)
                } else {
                    SessionDecision.Ignore
                }

            is SessionEvent.UserPresent ->
                if (mode.requiresLockedScreen) {
                    SessionDecision.Finish(EndReason.SCREEN_UNLOCKED)
                } else {
                    SessionDecision.Ignore
                }

            is SessionEvent.ScreenOn ->
                // Glancing at the clock on a locked phone is allowed. But if no
                // keyguard stands in the way, the phone is open the instant it lights up.
                if (mode.requiresLockedScreen && !event.keyguardLocked) {
                    SessionDecision.Finish(EndReason.SCREEN_UNLOCKED)
                } else {
                    SessionDecision.Ignore
                }

            is SessionEvent.ScreenOff, is SessionEvent.UserStopped -> SessionDecision.Ignore
        }
    }
}
