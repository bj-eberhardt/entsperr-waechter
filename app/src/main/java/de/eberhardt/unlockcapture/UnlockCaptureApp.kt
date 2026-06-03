package de.eberhardt.unlockcapture

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import de.eberhardt.unlockcapture.capture.CaptureForegroundService
import de.eberhardt.unlockcapture.capture.CaptureTrigger
import de.eberhardt.unlockcapture.notify.FailedUnlockNotifier
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.util.AppLog

class UnlockCaptureApp : Application() {
    companion object {
        const val ENABLE_SCREEN_UNLOCK_TRIGGER = false
    }

    private var lastScreenOnMs: Long = 0L
    private var lastTriggerMs: Long = 0L

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val now = System.currentTimeMillis()
            AppLog.i("RuntimeRx", "onReceive action=$action")

            if (!ENABLE_SCREEN_UNLOCK_TRIGGER) return

            when (action) {
                Intent.ACTION_SCREEN_ON -> {
                    lastScreenOnMs = now
                    AppLog.i("RuntimeRx", "screenOn ts=$lastScreenOnMs")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    lastScreenOnMs = 0L
                    AppLog.i("RuntimeRx", "screenOff -> reset")
                }
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_USER_UNLOCKED -> {
                    val screenOnAgeMs = if (lastScreenOnMs == 0L) Long.MAX_VALUE else (now - lastScreenOnMs)
                    val sinceLastTriggerMs = now - lastTriggerMs
                    val withinWindow = screenOnAgeMs in 0..30_000
                    val debounced = sinceLastTriggerMs < 30_000

                    AppLog.i(
                        "RuntimeRx",
                        "unlockSignal action=$action withinWindow=$withinWindow screenOnAgeMs=$screenOnAgeMs debounced=$debounced"
                    )

                    if (withinWindow && !debounced) {
                        lastTriggerMs = now
                        AppLog.i("RuntimeRx", "Trigger capture (reason=USER_PRESENT)")
                        CaptureTrigger.start(applicationContext, CaptureReason.USER_PRESENT)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.i("App", "onCreate() pid=${android.os.Process.myPid()} sdk=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CaptureForegroundService.CHANNEL_ID,
                    getString(R.string.capture_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.capture_channel_desc)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    FailedUnlockNotifier.CHANNEL_ID,
                    getString(R.string.failed_unlock_warning_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.failed_unlock_warning_channel_desc)
                }
            )
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            if (BuildConfig.DEBUG) {
                addAction("de.eberhardt.unlockcapture.SELF_TEST")
            }
        }
        ContextCompat.registerReceiver(
            /* context = */ this,
            /* receiver = */ runtimeReceiver,
            /* filter = */ filter,
            /* flags = */ ContextCompat.RECEIVER_NOT_EXPORTED
        )
        AppLog.i("App", "Registered runtime receiver for USER_PRESENT/USER_UNLOCKED/SCREEN_ON/OFF")

        if (BuildConfig.DEBUG) {
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    sendBroadcast(Intent("de.eberhardt.unlockcapture.SELF_TEST").setPackage(packageName))
                }.onSuccess {
                    AppLog.i("App", "Sent self-test broadcast SELF_TEST (package-scoped)")
                }.onFailure {
                    AppLog.w("App", "Failed to send self-test broadcast", it)
                }
            }, 1500)
        }
    }
}
