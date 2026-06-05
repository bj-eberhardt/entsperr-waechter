package de.eberhardt.unlockcapture.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import de.eberhardt.unlockcapture.events.UnlockEventHandler
import de.eberhardt.unlockcapture.util.AppLog
import de.eberhardt.unlockcapture.util.BroadcastAsync

class UnlockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Admin", "onReceive action=${intent.action} extras=${intent.extras?.keySet()?.joinToString(",") ?: "-"}")
        super.onReceive(context, intent)
    }

    override fun onEnabled(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Admin", "onEnabled()")
        super.onEnabled(context, intent)
    }

    override fun onDisabled(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Admin", "onDisabled()")
        super.onDisabled(context, intent)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated DeviceAdminReceiver callback (kept for compatibility).")
    override fun onPasswordFailed(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Admin", "onPasswordFailed extras=${intent.extras?.keySet()?.joinToString(",") ?: "-"}")
        launchAsync { UnlockEventHandler(context).onPasswordFailed() }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated DeviceAdminReceiver callback (kept for compatibility).")
    override fun onPasswordSucceeded(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Admin", "onPasswordSucceeded extras=${intent.extras?.keySet()?.joinToString(",") ?: "-"}")
        launchAsync { UnlockEventHandler(context).onPasswordSucceeded() }
    }

    private fun launchAsync(block: suspend () -> Unit) {
        BroadcastAsync.launch(
            pendingResult = goAsync(),
            tag = "Admin",
            block = block,
        )
    }
}
