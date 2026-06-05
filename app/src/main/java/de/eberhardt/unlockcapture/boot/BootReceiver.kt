package de.eberhardt.unlockcapture.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.util.AppLog

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AppLog.i("Boot", "onReceive action=${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Toast.makeText(context, context.getString(R.string.boot_ready_toast), Toast.LENGTH_SHORT).show()
        }
    }
}
