package de.eberhardt.unlockcapture.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.eberhardt.unlockcapture.MainActivity
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.settings.FailedUnlockWarningStats
import de.eberhardt.unlockcapture.util.AppLog
import de.eberhardt.unlockcapture.util.PermissionUtils

object FailedUnlockNotifier {
    const val CHANNEL_ID = "failed_unlock_warning"
    private const val NOTIFICATION_ID = 2001

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        stats: FailedUnlockWarningStats,
    ) {
        val appContext = context.applicationContext
        if (!PermissionUtils.hasNotifications(appContext)) {
            AppLog.w("FailedUnlockNotifier", "Notification permission missing -> skip warning")
            return
        }

        val text =
            appContext.resources.getQuantityString(
                R.plurals.failed_unlock_warning_count,
                stats.count,
                stats.count,
            )
        val pending =
            PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(appContext.getString(R.string.failed_unlock_warning_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setNumber(stats.count)
                .setWhen(stats.lastTimestampMs)
                .setShowWhen(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }.onFailure {
            AppLog.w("FailedUnlockNotifier", "Failed to show warning notification", it)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }
}
