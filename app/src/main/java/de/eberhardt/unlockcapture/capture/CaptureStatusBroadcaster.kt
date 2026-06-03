package de.eberhardt.unlockcapture.capture

import android.content.Context
import android.content.Intent
import de.eberhardt.unlockcapture.settings.CaptureReason

class CaptureStatusBroadcaster(private val context: Context) {
    fun started(reason: CaptureReason) {
        send(reason, CaptureForegroundService.STATE_STARTED, success = true, error = null)
    }

    fun finished(reason: CaptureReason, success: Boolean, error: String? = null) {
        send(reason, CaptureForegroundService.STATE_FINISHED, success = success, error = error)
    }

    private fun send(reason: CaptureReason, state: String, success: Boolean, error: String?) {
        val intent = Intent(CaptureForegroundService.ACTION_CAPTURE_STATUS).apply {
            setPackage(context.packageName)
            putExtra(CaptureForegroundService.EXTRA_REASON, reason.name)
            putExtra(CaptureForegroundService.EXTRA_STATE, state)
            putExtra(CaptureForegroundService.EXTRA_SUCCESS, success)
            if (error != null) putExtra(CaptureForegroundService.EXTRA_ERROR, error)
        }
        context.sendBroadcast(intent)
    }
}
