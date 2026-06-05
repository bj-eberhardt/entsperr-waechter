package de.eberhardt.unlockcapture.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.util.AppLog

object CaptureTrigger {
    fun start(
        context: Context,
        reason: CaptureReason,
    ) {
        AppLog.i("Trigger", "startForegroundService reason=$reason")
        val intent =
            Intent(context, CaptureForegroundService::class.java)
                .putExtra(CaptureForegroundService.EXTRA_REASON, reason.name)
        ContextCompat.startForegroundService(context, intent)
    }
}
