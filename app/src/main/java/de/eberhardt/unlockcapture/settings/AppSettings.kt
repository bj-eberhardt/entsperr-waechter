package de.eberhardt.unlockcapture.settings

data class AppSettings(
    val captureMode: CaptureMode,
    val videoDurationSeconds: Int,
    val unlockLoggingMode: UnlockLoggingMode,
    val lockEnabled: Boolean,
    val lockTimeoutMs: Long,
    val lastAuthElapsedMs: Long,
    val failedUnlockWarningEnabled: Boolean,
)

enum class CaptureMode { PHOTO, VIDEO_4_SECONDS }

enum class CaptureReason { PASSWORD_FAILED, PASSWORD_SUCCEEDED, USER_PRESENT, MANUAL_TEST }

enum class UnlockLoggingMode {
    FAILED_ONLY,
    ALL,
}
