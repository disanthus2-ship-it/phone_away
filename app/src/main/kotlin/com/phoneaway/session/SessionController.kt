package com.phoneaway.session

import com.phoneaway.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single place the running session is published from.
 *
 * [IdleSessionService] owns the clock and writes here; the UI only reads. That
 * keeps the timer authoritative in the service, which is the one component
 * guaranteed to stay alive while the screen is off.
 */
class SessionController {

    private val _state = MutableStateFlow(ActiveSessionState())
    val state: StateFlow<ActiveSessionState> = _state.asStateFlow()

    /** Set when a session ends, so the UI can show the result once it returns. */
    private val _lastResult = MutableStateFlow<Session?>(null)
    val lastResult: StateFlow<Session?> = _lastResult.asStateFlow()

    internal fun update(state: ActiveSessionState) {
        _state.value = state
    }

    /** Clears the active session without recording a result. */
    internal fun reset() {
        _state.value = ActiveSessionState()
    }

    internal fun publishResult(session: Session) {
        _state.value = ActiveSessionState()
        _lastResult.value = session
    }

    /** Called once the result screen has been shown. */
    fun consumeResult() {
        _lastResult.value = null
    }
}
