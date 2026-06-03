package de.eberhardt.unlockcapture.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.eberhardt.unlockcapture.capture.CaptureTrigger
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.util.AppLog

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i("Boot", "onReceive action=${intent.action}")
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            AppLog.i("Boot", "Trigger capture (reason=USER_PRESENT)")
            CaptureTrigger.start(context, CaptureReason.USER_PRESENT)
        }
    }
}
