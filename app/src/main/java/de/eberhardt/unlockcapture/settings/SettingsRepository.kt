package de.eberhardt.unlockcapture.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val captureModeKey = stringPreferencesKey("capture_mode")
    private val videoDurationSecondsKey = intPreferencesKey("video_duration_seconds")
    private val unlockLoggingModeKey = stringPreferencesKey("unlock_logging_mode")
    private val lockEnabledKey = booleanPreferencesKey("lock_enabled")
    private val lockTimeoutMsKey = longPreferencesKey("lock_timeout_ms")
    private val lastAuthElapsedMsKey = longPreferencesKey("last_auth_elapsed_ms")

    val videoDurationSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        val raw = prefs[videoDurationSecondsKey] ?: 4
        raw.coerceIn(3, 20)
    }

    val unlockLoggingMode: Flow<UnlockLoggingMode> = context.dataStore.data.map { prefs ->
        runCatching { UnlockLoggingMode.valueOf(prefs[unlockLoggingModeKey] ?: UnlockLoggingMode.FAILED_ONLY.name) }
            .getOrDefault(UnlockLoggingMode.FAILED_ONLY)
    }

    val lockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[lockEnabledKey] ?: false
    }

    val lockTimeoutMs: Flow<Long> = context.dataStore.data.map { prefs ->
        val raw = prefs[lockTimeoutMsKey] ?: 0L
        when (raw) {
            0L, 30_000L, 300_000L -> raw
            else -> 0L
        }
    }

    val lastAuthElapsedMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[lastAuthElapsedMsKey] ?: 0L
    }

    val captureMode: Flow<CaptureMode> = context.dataStore.data.map { prefs ->
        runCatching { CaptureMode.valueOf(prefs[captureModeKey] ?: CaptureMode.PHOTO.name) }
            .getOrDefault(CaptureMode.PHOTO)
    }

    suspend fun setCaptureMode(mode: CaptureMode) {
        context.dataStore.edit { it[captureModeKey] = mode.name }
    }

    suspend fun setVideoDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(3, 20)
        context.dataStore.edit { it[videoDurationSecondsKey] = clamped }
    }

    suspend fun setUnlockLoggingMode(mode: UnlockLoggingMode) {
        context.dataStore.edit { it[unlockLoggingModeKey] = mode.name }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[lockEnabledKey] = enabled }
    }

    suspend fun setLockTimeoutMs(timeoutMs: Long) {
        val normalized = when (timeoutMs) {
            0L, 30_000L, 300_000L -> timeoutMs
            else -> 0L
        }
        context.dataStore.edit { it[lockTimeoutMsKey] = normalized }
    }

    suspend fun setLastAuthElapsedMs(elapsedMs: Long) {
        context.dataStore.edit { it[lastAuthElapsedMsKey] = elapsedMs }
    }
}
