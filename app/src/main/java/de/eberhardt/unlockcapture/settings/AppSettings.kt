package de.eberhardt.unlockcapture.settings

enum class CaptureMode { PHOTO, VIDEO_4_SECONDS }

enum class CaptureReason { PASSWORD_FAILED, PASSWORD_SUCCEEDED, USER_PRESENT, MANUAL_TEST }

enum class UnlockLoggingMode {
    FAILED_ONLY,
    ALL,
}
