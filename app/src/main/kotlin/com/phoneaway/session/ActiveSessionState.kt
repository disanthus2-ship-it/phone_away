package com.phoneaway.session

import com.phoneaway.core.AwayMode
import com.phoneaway.core.SessionPhase

data class ActiveSessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val mode: AwayMode = AwayMode.SCREEN_LOCKED,
    val focusedSeconds: Long = 0,
    val targetSeconds: Long? = null,
    val projectedPoints: Int = 0,
    val streakDays: Int = 0,
) {
    val isActive: Boolean get() = phase != SessionPhase.IDLE

    /** 0f..1f toward the target, or null for an open-ended run. */
    val progress: Float?
        get() = targetSeconds?.let { target ->
            if (target <= 0) null else (focusedSeconds.toFloat() / target).coerceIn(0f, 1f)
        }

    val remainingSeconds: Long?
        get() = targetSeconds?.let { (it - focusedSeconds).coerceAtLeast(0) }
}
