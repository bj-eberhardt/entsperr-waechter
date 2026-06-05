package de.eberhardt.unlockcapture.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.eberhardt.unlockcapture.events.UnlockEventHandler
import de.eberhardt.unlockcapture.util.AppLog
import de.eberhardt.unlockcapture.util.BroadcastAsync

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Boot", "onReceive action=${intent.action}")
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            AppLog.i("Boot", "Trigger capture (reason=USER_PRESENT)")
            BroadcastAsync.launch(
                pendingResult = goAsync(),
                tag = "Boot",
            ) {
                UnlockEventHandler(context).onUserPresent()
            }
        }
    }
}
