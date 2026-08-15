package com.phoneaway.session

import android.content.Context
import android.os.SystemClock
import com.phoneaway.core.AwayMode
import com.phoneaway.core.SessionPhase

/**
 * A crash-recovery record for the session in progress.
 *
 * Foreground services are rarely killed, but losing a two-hour run to a memory
 * kill would be exactly the sort of unfairness that makes people stop trusting
 * the app. This is small enough to write on every phase change.
 */
class ActiveSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("active_session", Context.MODE_PRIVATE)

    data class Snapshot(
        val mode: AwayMode,
        val phase: SessionPhase,
        val targetSeconds: Long?,
        val startedAtEpochSeconds: Long,
        val clockBaseUptimeMillis: Long,
        val streakDays: Int,
    )

    fun save(snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_MODE, snapshot.mode.id)
            .putString(KEY_PHASE, snapshot.phase.name)
            .putLong(KEY_TARGET, snapshot.targetSeconds ?: 0L)
            .putLong(KEY_STARTED_AT, snapshot.startedAtEpochSeconds)
            .putLong(KEY_CLOCK_BASE, snapshot.clockBaseUptimeMillis)
            .putInt(KEY_STREAK, snapshot.streakDays)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /**
     * The interrupted session, or null if there was none.
     *
     * [SystemClock.elapsedRealtime] resets to zero on reboot, so a stored base
     * in the future is proof the phone restarted -- and a session cannot
     * survive a reboot, so it is dropped rather than credited with bogus time.
     */
    fun restore(): Snapshot? {
        val modeId = prefs.getString(KEY_MODE, null) ?: return null
        val clockBase = prefs.getLong(KEY_CLOCK_BASE, -1L)
        if (clockBase < 0 || clockBase > SystemClock.elapsedRealtime()) {
            clear()
            return null
        }

        return Snapshot(
            mode = AwayMode.fromId(modeId),
            phase = runCatching { SessionPhase.valueOf(prefs.getString(KEY_PHASE, "")!!) }
                .getOrDefault(SessionPhase.RUNNING),
            targetSeconds = prefs.getLong(KEY_TARGET, 0L).takeIf { it > 0 },
            startedAtEpochSeconds = prefs.getLong(KEY_STARTED_AT, 0L),
            clockBaseUptimeMillis = clockBase,
            streakDays = prefs.getInt(KEY_STREAK, 0),
        )
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_PHASE = "phase"
        const val KEY_TARGET = "target"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_CLOCK_BASE = "clock_base"
        const val KEY_STREAK = "streak"
    }
}
