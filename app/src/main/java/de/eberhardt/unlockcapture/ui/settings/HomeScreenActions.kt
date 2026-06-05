package de.eberhardt.unlockcapture.ui.settings

import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode

internal data class HomeScreenActions(
    val onVideoDurationSeconds: (Int) -> Unit,
    val onUnlockLoggingMode: (UnlockLoggingMode) -> Unit,
    val onFailedUnlockWarningEnabled: (Boolean) -> Unit,
    val onRequestNotifications: () -> Unit,
    val onLockEnabled: (Boolean) -> Unit,
    val onLockTimeoutMs: (Long) -> Unit,
    val onMode: (CaptureMode) -> Unit,
    val onTest: () -> Unit,
    val onOpenFolder: () -> Unit,
)
