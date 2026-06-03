package de.eberhardt.unlockcapture.events

import android.content.Context
import de.eberhardt.unlockcapture.audit.AuditLog
import de.eberhardt.unlockcapture.capture.CaptureTrigger
import de.eberhardt.unlockcapture.notify.FailedUnlockNotifier
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.settings.SettingsRepository
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode
import kotlinx.coroutines.flow.first

class UnlockEventHandler(
    context: Context,
    private val settings: SettingsRepository = SettingsRepository(context.applicationContext),
) {
    private val appContext = context.applicationContext

    suspend fun onPasswordFailed() {
        AuditLog.appendUnlockEvent(
            context = appContext,
            eventKey = AuditLog.EVENT_UNLOCK_FAILED,
            result = "FAIL"
        )
        if (settings.failedUnlockWarningEnabled.first()) {
            FailedUnlockNotifier.show(appContext, settings.recordFailedUnlockWarning())
        }
        CaptureTrigger.start(appContext, CaptureReason.PASSWORD_FAILED)
    }

    suspend fun onPasswordSucceeded() {
        if (settings.unlockLoggingMode.first() != UnlockLoggingMode.ALL) return
        AuditLog.appendUnlockEvent(
            context = appContext,
            eventKey = AuditLog.EVENT_UNLOCK_SUCCESS,
            result = "SUCCESS"
        )
        CaptureTrigger.start(appContext, CaptureReason.PASSWORD_SUCCEEDED)
    }

    suspend fun onUserPresent() {
        CaptureTrigger.start(appContext, CaptureReason.USER_PRESENT)
    }
}
