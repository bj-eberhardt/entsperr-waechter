package de.eberhardt.unlockcapture.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class FailedUnlockWarningStats(
    val count: Int,
    val lastTimestampMs: Long,
)

class SettingsRepository(
    private val context: Context,
) {
    private val captureModeKey = stringPreferencesKey("capture_mode")
    private val videoDurationSecondsKey = intPreferencesKey("video_duration_seconds")
    private val unlockLoggingModeKey = stringPreferencesKey("unlock_logging_mode")
    private val lockEnabledKey = booleanPreferencesKey("lock_enabled")
    private val lockTimeoutMsKey = longPreferencesKey("lock_timeout_ms")
    private val lastAuthElapsedMsKey = longPreferencesKey("last_auth_elapsed_ms")
    private val failedUnlockWarningEnabledKey = booleanPreferencesKey("failed_unlock_warning_enabled")
    private val failedUnlockWarningCountKey = intPreferencesKey("failed_unlock_warning_count")
    private val failedUnlockWarningLastTimestampMsKey = longPreferencesKey("failed_unlock_warning_last_timestamp_ms")

    val appSettings: Flow<AppSettings> =
        context.dataStore.data.map { prefs ->
            AppSettings(
                captureMode =
                runCatching { CaptureMode.valueOf(prefs[captureModeKey] ?: CaptureMode.PHOTO.name) }
                    .getOrDefault(CaptureMode.PHOTO),
                videoDurationSeconds = (prefs[videoDurationSecondsKey] ?: 4).coerceIn(3, 20),
                unlockLoggingMode =
                runCatching { UnlockLoggingMode.valueOf(prefs[unlockLoggingModeKey] ?: UnlockLoggingMode.FAILED_ONLY.name) }
                    .getOrDefault(UnlockLoggingMode.FAILED_ONLY),
                lockEnabled = prefs[lockEnabledKey] ?: false,
                lockTimeoutMs = normalizeLockTimeoutMs(prefs[lockTimeoutMsKey] ?: 0L),
                lastAuthElapsedMs = prefs[lastAuthElapsedMsKey] ?: 0L,
                failedUnlockWarningEnabled = prefs[failedUnlockWarningEnabledKey] ?: false,
            )
        }

    val videoDurationSeconds: Flow<Int> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[videoDurationSecondsKey] ?: 4
            raw.coerceIn(3, 20)
        }

    val unlockLoggingMode: Flow<UnlockLoggingMode> =
        context.dataStore.data.map { prefs ->
            runCatching { UnlockLoggingMode.valueOf(prefs[unlockLoggingModeKey] ?: UnlockLoggingMode.FAILED_ONLY.name) }
                .getOrDefault(UnlockLoggingMode.FAILED_ONLY)
        }

    val lockEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[lockEnabledKey] ?: false
        }

    val lockTimeoutMs: Flow<Long> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[lockTimeoutMsKey] ?: 0L
            normalizeLockTimeoutMs(raw)
        }

    val lastAuthElapsedMs: Flow<Long> =
        context.dataStore.data.map { prefs ->
            prefs[lastAuthElapsedMsKey] ?: 0L
        }

    val failedUnlockWarningEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[failedUnlockWarningEnabledKey] ?: false
        }

    val captureMode: Flow<CaptureMode> =
        context.dataStore.data.map { prefs ->
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
        val normalized = normalizeLockTimeoutMs(timeoutMs)
        context.dataStore.edit { it[lockTimeoutMsKey] = normalized }
    }

    suspend fun setLastAuthElapsedMs(elapsedMs: Long) {
        context.dataStore.edit { it[lastAuthElapsedMsKey] = elapsedMs }
    }

    suspend fun setFailedUnlockWarningEnabled(enabled: Boolean) {
        context.dataStore.edit { it[failedUnlockWarningEnabledKey] = enabled }
    }

    suspend fun recordFailedUnlockWarning(): FailedUnlockWarningStats {
        var stats = FailedUnlockWarningStats(count = 1, lastTimestampMs = System.currentTimeMillis())
        context.dataStore.edit { prefs ->
            val count = ((prefs[failedUnlockWarningCountKey] ?: 0) + 1).coerceAtLeast(1)
            val timestampMs = System.currentTimeMillis()
            prefs[failedUnlockWarningCountKey] = count
            prefs[failedUnlockWarningLastTimestampMsKey] = timestampMs
            stats = FailedUnlockWarningStats(count = count, lastTimestampMs = timestampMs)
        }
        return stats
    }

    suspend fun resetFailedUnlockWarningStats() {
        context.dataStore.edit { prefs ->
            prefs.remove(failedUnlockWarningCountKey)
            prefs.remove(failedUnlockWarningLastTimestampMsKey)
        }
    }

    private fun normalizeLockTimeoutMs(timeoutMs: Long): Long = when (timeoutMs) {
        0L, 30_000L, 300_000L -> timeoutMs
        else -> 0L
    }
}
