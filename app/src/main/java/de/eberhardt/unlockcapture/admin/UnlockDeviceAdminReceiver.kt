package de.eberhardt.unlockcapture.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import de.eberhardt.unlockcapture.audit.AuditLog
import de.eberhardt.unlockcapture.capture.CaptureTrigger
import de.eberhardt.unlockcapture.notify.FailedUnlockNotifier
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.settings.SettingsRepository
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode
import de.eberhardt.unlockcapture.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UnlockDeviceAdminReceiver : DeviceAdminReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i("Admin", "onReceive action=${intent.action} extras=${intent.extras?.keySet()?.joinToString(",") ?: "-"}")
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        AppLog.i("Admin", "onEnabled()")
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        AppLog.i("Admin", "onDisabled()")
        super.onDisabled(context, intent)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated DeviceAdminReceiver callback (kept for compatibility).")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        AppLog.i("Admin", "onPasswordFailed extras=${intent.extras?.keySet()?.joinToString(",") ?: "-"}")
        AuditLog.appendUnlockEvent(
            context = context.applicationContext,
            eventKey = AuditLog.EVENT_UNLOCK_FAILED,
            result = "FAIL"
        )
        scope.launch {
            val settings = SettingsRepository(context.applicationContext)
            val enabled = settings.failedUnlockWarningEnabled.first()
            if (enabled) {
                FailedUnlockNotifier.show(context, settings.recordFailedUnlockWarning())
            }
        }
        CaptureTrigger.start(context, CaptureReason.PASSWORD_FAILED)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated DeviceAdminReceiver callback (kept for compatibility).")
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        AppLog.i("Admin", "onPasswordSucceeded extras=${intent.extras?.keySet()?.joinToString(",") ?: "-"}")
        scope.launch {
            val mode = SettingsRepository(context.applicationContext).unlockLoggingMode.first()
            if (mode == UnlockLoggingMode.ALL) {
                AuditLog.appendUnlockEvent(
                    context = context.applicationContext,
                    eventKey = AuditLog.EVENT_UNLOCK_SUCCESS,
                    result = "SUCCESS"
                )
                CaptureTrigger.start(context, CaptureReason.PASSWORD_SUCCEEDED)
            }
        }
    }
}
