package com.phoneaway.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneaway.PhoneAwayApplication
import com.phoneaway.core.AwayMode
import com.phoneaway.core.Session
import com.phoneaway.core.Stats
import com.phoneaway.core.StatsCalculator
import com.phoneaway.session.ActiveSessionState
import com.phoneaway.session.IdleSessionService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PhoneAwayApplication
    private val repository = app.sessionRepository
    private val controller = app.sessionController
    private val zone: ZoneId = ZoneId.systemDefault()

    val activeSession: StateFlow<ActiveSessionState> = controller.state
    val lastResult: StateFlow<Session?> = controller.lastResult
    val sessions: StateFlow<List<Session>> = repository.sessions

    val stats: StateFlow<Stats> = repository.sessions
        .map { StatsCalculator.compute(it, zone, LocalDate.now(zone)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsCalculator.compute(emptyList(), zone, LocalDate.now(zone)),
        )

    fun startSession(context: Context, mode: AwayMode, targetSeconds: Long?) {
        context.startForegroundService(IdleSessionService.startIntent(context, mode, targetSeconds))
    }

    fun stopSession(context: Context) {
        context.startService(IdleSessionService.stopIntent(context))
    }

    fun dismissResult() = controller.consumeResult()

    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }
}
