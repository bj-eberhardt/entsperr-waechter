package de.eberhardt.unlockcapture.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.eberhardt.unlockcapture.events.UnlockEventHandler
import de.eberhardt.unlockcapture.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i("Boot", "onReceive action=${intent.action}")
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            AppLog.i("Boot", "Trigger capture (reason=USER_PRESENT)")
            val pendingResult = goAsync()
            scope.launch {
                try {
                    UnlockEventHandler(context).onUserPresent()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
