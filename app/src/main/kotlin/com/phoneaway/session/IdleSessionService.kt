package com.phoneaway.session

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.phoneaway.PhoneAwayApplication
import com.phoneaway.core.AwayMode
import com.phoneaway.core.EndReason
import com.phoneaway.core.ScoreEngine
import com.phoneaway.core.SessionDecision
import com.phoneaway.core.SessionEvent
import com.phoneaway.core.SessionFactory
import com.phoneaway.core.SessionPhase
import com.phoneaway.core.SessionRules
import com.phoneaway.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Owns the running session: the clock, the break rules, and the record written
 * at the end.
 *
 * This lives in a foreground service because the whole point of the app is that
 * the phone is locked and the UI is gone. Elapsed time is measured with
 * [SystemClock.elapsedRealtime] rather than by counting ticks, so a tick
 * deferred by Doze costs display smoothness but never costs the user credit.
 */
class IdleSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    private lateinit var controller: SessionController
    private lateinit var repository: SessionRepository
    private lateinit var notifications: SessionNotifications
    private lateinit var activeStore: ActiveSessionStore

    private var mode: AwayMode = AwayMode.HONOR
    private var targetSeconds: Long? = null
    private var streakDays: Int = 0

    private var startedAt: Instant = Instant.EPOCH
    private var clockStartedAtUptime: Long = 0L
    private var phase: SessionPhase = SessionPhase.IDLE

    private var stillnessDetector: StillnessDetector? = null

    /** Registered at runtime -- screen on/off cannot be received from the manifest. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handle(SessionEvent.ScreenOff)
                Intent.ACTION_SCREEN_ON -> handle(SessionEvent.ScreenOn(isKeyguardLocked()))
                Intent.ACTION_USER_PRESENT -> handle(SessionEvent.UserPresent)
            }
        }
    }

    /** Only used by [AwayMode.APP_OPEN], where leaving the app is the failure. */
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = handle(SessionEvent.AppBackgrounded)
    }

    /**
     * The single funnel for anything that could change the session. What each
     * event means per mode lives in [SessionRules], which is unit-tested.
     */
    private fun handle(event: SessionEvent) {
        when (val decision = SessionRules.decide(mode, phase, event)) {
            is SessionDecision.Ignore -> Unit
            is SessionDecision.BeginRunning -> beginRunning()
            is SessionDecision.Finish -> finish(decision.reason)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as PhoneAwayApplication
        controller = app.sessionController
        repository = app.sessionRepository
        notifications = SessionNotifications(this)
        notifications.createChannels()
        activeStore = ActiveSessionStore(this)

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(
                mode = AwayMode.fromId(intent.getStringExtra(EXTRA_MODE).orEmpty()),
                targetSeconds = intent.getLongExtra(EXTRA_TARGET_SECONDS, 0L).takeIf { it > 0 },
            )

            // A stop for a session that is already over should not leave the
            // service sitting around.
            ACTION_STOP -> {
                handle(SessionEvent.UserStopped)
                if (phase == SessionPhase.IDLE) stopSelf()
            }

            // A null intent means the system restarted us after a kill. Pick the
            // session back up rather than silently dropping the user's time.
            null -> if (phase == SessionPhase.IDLE && !restoreInterruptedSession()) stopSelf()

            else -> if (phase == SessionPhase.IDLE) stopSelf()
        }
        return START_STICKY
    }

    private fun restoreInterruptedSession(): Boolean {
        val saved = activeStore.restore() ?: return false

        mode = saved.mode
        targetSeconds = saved.targetSeconds
        startedAt = Instant.ofEpochSecond(saved.startedAtEpochSeconds)
        streakDays = saved.streakDays
        phase = saved.phase
        clockStartedAtUptime = saved.clockBaseUptimeMillis

        startForeground(SessionNotifications.ONGOING_ID, notifications.ongoing(snapshot()))

        // App Open mode is defined by this app staying in the foreground, and a
        // process death means it did not. Credit the time honestly as a break
        // rather than resuming a session whose premise no longer holds.
        if (mode == AwayMode.APP_OPEN) {
            finish(EndReason.APP_LEFT)
            return true
        }

        if (phase == SessionPhase.RUNNING) resumeClock() else publish()
        return true
    }

    private fun start(mode: AwayMode, targetSeconds: Long?) {
        if (phase != SessionPhase.IDLE) return

        this.mode = mode
        this.targetSeconds = targetSeconds
        this.startedAt = Instant.now()
        this.streakDays = repository.streakForScoring()

        phase = SessionRules.initialPhase(mode, screenIsOn = isScreenInteractive())
        clockStartedAtUptime = SystemClock.elapsedRealtime()

        startForeground(SessionNotifications.ONGOING_ID, notifications.ongoing(snapshot()))
        publish()
        persistActive()

        if (mode == AwayMode.APP_OPEN) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        }
        if (phase == SessionPhase.RUNNING) {
            beginRunning()
        }
    }

    /** Starts the clock from zero. */
    private fun beginRunning() {
        phase = SessionPhase.RUNNING
        clockStartedAtUptime = SystemClock.elapsedRealtime()
        resumeClock()
    }

    /** Starts ticking against whatever [clockStartedAtUptime] already holds. */
    private fun resumeClock() {
        phase = SessionPhase.RUNNING
        persistActive()

        if (mode.requiresStillness) {
            // Sensor callbacks arrive on the main looper, same thread as everything
            // else here, so no extra synchronisation is needed.
            stillnessDetector = StillnessDetector(this) { handle(SessionEvent.DeviceMoved) }
                .also { it.start() }
        }

        ticker?.cancel()
        ticker = scope.launch {
            var lastNotificationSeconds = -1L
            while (isActive && phase == SessionPhase.RUNNING) {
                val elapsed = elapsedSeconds()
                val target = targetSeconds
                if (target != null && elapsed >= target) {
                    handle(SessionEvent.TargetReached)
                    return@launch
                }

                publish()
                // The visible clock lives in the app; the notification only
                // needs to be roughly right, so refresh it sparingly.
                if (elapsed - lastNotificationSeconds >= NOTIFICATION_REFRESH_SECONDS) {
                    lastNotificationSeconds = elapsed
                    startForeground(SessionNotifications.ONGOING_ID, notifications.ongoing(snapshot()))
                }
                delay(1_000)
            }
        }
    }

    private fun persistActive() = activeStore.save(
        ActiveSessionStore.Snapshot(
            mode = mode,
            phase = phase,
            targetSeconds = targetSeconds,
            startedAtEpochSeconds = startedAt.epochSecond,
            clockBaseUptimeMillis = clockStartedAtUptime,
            streakDays = streakDays,
        ),
    )

    private fun finish(reason: EndReason) {
        if (phase == SessionPhase.IDLE) return

        val focusedSeconds = elapsedSeconds()
        val target = targetSeconds

        phase = SessionPhase.IDLE
        ticker?.cancel()
        ticker = null
        stillnessDetector?.stop()
        stillnessDetector = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        activeStore.clear()

        // Backing out before the clock ever started is not a failed attempt --
        // recording it would clutter history and dent the success rate.
        if (focusedSeconds < 1L) {
            controller.reset()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val session = SessionFactory.build(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAt = startedAt,
            endedAt = Instant.now(),
            focusedSeconds = focusedSeconds,
            targetSeconds = target,
            endReason = reason,
            streakDays = streakDays,
        )

        scope.launch { repository.add(session) }
        controller.publishResult(session)

        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(SessionNotifications.RESULT_ID, notifications.result(session)) }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Seconds on the clock. Zero while arming, since the run has not begun. */
    private fun elapsedSeconds(): Long = when (phase) {
        SessionPhase.ARMING, SessionPhase.IDLE -> 0L
        SessionPhase.RUNNING -> (SystemClock.elapsedRealtime() - clockStartedAtUptime) / 1000
    }

    private fun isScreenInteractive(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false

    private fun snapshot(): ActiveSessionState {
        val elapsed = elapsedSeconds()
        return ActiveSessionState(
            phase = phase,
            mode = mode,
            focusedSeconds = elapsed,
            targetSeconds = targetSeconds,
            projectedPoints = ScoreEngine.projectedPoints(mode, elapsed, streakDays),
            streakDays = streakDays,
        )
    }

    private fun publish() = controller.update(snapshot())

    override fun onDestroy() {
        ticker?.cancel()
        stillnessDetector?.stop()
        runCatching { unregisterReceiver(screenReceiver) }
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.phoneaway.action.START"
        const val ACTION_STOP = "com.phoneaway.action.STOP"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TARGET_SECONDS = "target_seconds"
        private const val NOTIFICATION_REFRESH_SECONDS = 10L

        fun startIntent(context: Context, mode: AwayMode, targetSeconds: Long?): Intent =
            Intent(context, IdleSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.id)
                putExtra(EXTRA_TARGET_SECONDS, targetSeconds ?: 0L)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, IdleSessionService::class.java).apply { action = ACTION_STOP }
    }
}
