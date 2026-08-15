package com.phoneaway.data

import android.content.Context
import com.phoneaway.core.Session
import com.phoneaway.core.Stats
import com.phoneaway.core.StatsCalculator
import com.phoneaway.core.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Session history, persisted as a single JSON file.
 *
 * Everything the app shows -- points, level, streak -- is derived from this
 * list, so there is no separate total that can drift out of sync with it.
 */
class SessionRepository(
    context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val writeLock = Mutex()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    init {
        // Loaded synchronously: a session can be started seconds after launch,
        // and scoring it against an empty history would silently lose the
        // user's streak bonus. The file holds one small record per session.
        _sessions.value = load()
    }

    private fun load(): List<Session> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredSession>>(file.readText())
                .map(StoredSession::toSession)
                .sortedByDescending { it.startedAt }
        }.getOrElse {
            // A corrupt history file must never brick the app; start clean and
            // keep the bad file aside in case it is worth recovering by hand.
            runCatching { file.copyTo(File(file.parentFile, "$FILE_NAME.corrupt"), overwrite = true) }
            emptyList()
        }
    }

    suspend fun add(session: Session) {
        writeLock.withLock {
            val updated = (listOf(session) + _sessions.value).sortedByDescending { it.startedAt }
            _sessions.value = updated
            persist(updated)
        }
    }

    suspend fun clearHistory() {
        writeLock.withLock {
            _sessions.value = emptyList()
            persist(emptyList())
        }
    }

    private suspend fun persist(sessions: List<Session>) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(sessions.map(StoredSession::from))
        // Write to a temp file first so an interrupted write cannot truncate history.
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(payload)
        temp.renameTo(file)
    }

    fun statsNow(today: LocalDate = LocalDate.now(zone)): Stats =
        StatsCalculator.compute(_sessions.value, zone, today)

    /**
     * The streak a session starting now should be scored against. Read
     * synchronously because the service needs it the moment a run begins.
     */
    fun streakForScoring(today: LocalDate = LocalDate.now(zone)): Int =
        StreakCalculator.streakForScoring(
            StatsCalculator.qualifyingDates(_sessions.value, zone),
            today,
        )

    private companion object {
        const val FILE_NAME = "sessions.json"
    }
}
