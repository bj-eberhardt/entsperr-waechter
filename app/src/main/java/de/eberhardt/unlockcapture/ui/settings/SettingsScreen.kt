package de.eberhardt.unlockcapture.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode
import de.eberhardt.unlockcapture.ui.components.ModeRow
import de.eberhardt.unlockcapture.ui.components.RequirementCard

@Composable
internal fun SetupScreen(
    cameraOk: Boolean,
    notificationsOk: Boolean,
    adminOk: Boolean,
    mediaOk: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.setup_intro))

    RequirementCard(
        title = stringResource(R.string.setup_req_camera_title),
        ok = cameraOk,
        description = stringResource(R.string.setup_req_camera_desc),
        actionLabel = stringResource(R.string.action_grant_permissions),
        onAction = onRequestPermissions
    )
    RequirementCard(
        title = stringResource(R.string.setup_req_notifications_title),
        ok = notificationsOk,
        description = stringResource(R.string.setup_req_notifications_desc),
        actionLabel = stringResource(R.string.action_open_notification_settings),
        onAction = onOpenNotificationSettings
    )
    RequirementCard(
        title = stringResource(R.string.setup_req_media_title),
        ok = mediaOk,
        description = stringResource(R.string.setup_req_media_desc),
        actionLabel = stringResource(R.string.action_grant_permissions),
        onAction = onRequestPermissions
    )
    RequirementCard(
        title = stringResource(R.string.setup_req_admin_title),
        ok = adminOk,
        description = stringResource(R.string.setup_req_admin_desc),
        actionLabel = stringResource(R.string.action_enable_device_admin),
        onAction = onOpenDeviceAdmin
    )

    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_recheck)) }
}

@Composable
internal fun HomeScreen(
    mode: CaptureMode,
    videoDurationSeconds: Int,
    onVideoDurationSeconds: (Int) -> Unit,
    unlockLoggingMode: UnlockLoggingMode,
    onUnlockLoggingMode: (UnlockLoggingMode) -> Unit,
    failedUnlockWarningEnabled: Boolean,
    onFailedUnlockWarningEnabled: (Boolean) -> Unit,
    notificationsOk: Boolean,
    onRequestNotifications: () -> Unit,
    lockEnabled: Boolean,
    onLockEnabled: (Boolean) -> Unit,
    lockTimeoutMs: Long,
    onLockTimeoutMs: (Long) -> Unit,
    onMode: (CaptureMode) -> Unit,
    onTest: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Text(stringResource(R.string.home_status_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.home_status_body))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.capture_mode_title), style = MaterialTheme.typography.titleMedium)
            ModeRow(stringResource(R.string.capture_mode_photo), CaptureMode.PHOTO, mode, onMode)
            ModeRow(stringResource(R.string.capture_mode_video), CaptureMode.VIDEO_4_SECONDS, mode, onMode)
        }
    }
    if (mode == CaptureMode.VIDEO_4_SECONDS) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.video_duration_title), style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.video_duration_seconds, videoDurationSeconds, videoDurationSeconds))
                Slider(
                    value = videoDurationSeconds.toFloat(),
                    onValueChange = { onVideoDurationSeconds(it.toInt()) },
                    valueRange = 3f..20f,
                    steps = 16
                )
                Text(stringResource(R.string.video_duration_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.unlock_logging_title), style = MaterialTheme.typography.titleMedium)
            ModeRow(stringResource(R.string.unlock_logging_failed_only), UnlockLoggingMode.FAILED_ONLY, unlockLoggingMode, onUnlockLoggingMode)
            ModeRow(stringResource(R.string.unlock_logging_all), UnlockLoggingMode.ALL, unlockLoggingMode, onUnlockLoggingMode)
            Text(stringResource(R.string.unlock_logging_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.failed_unlock_warning_setting_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.failed_unlock_warning_setting_desc), modifier = Modifier.weight(1f))
                Switch(checked = failedUnlockWarningEnabled, onCheckedChange = onFailedUnlockWarningEnabled)
            }
            if (!notificationsOk) {
                Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_allow_notifications))
                }
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.app_lock_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_lock_enabled), modifier = Modifier.weight(1f))
                Switch(checked = lockEnabled, onCheckedChange = onLockEnabled)
            }
            if (lockEnabled) {
                Text(stringResource(R.string.app_lock_timeout_title), style = MaterialTheme.typography.titleSmall)
                ModeRow(stringResource(R.string.app_lock_timeout_immediate), 0L, lockTimeoutMs, onLockTimeoutMs)
                ModeRow(stringResource(R.string.app_lock_timeout_30s), 30_000L, lockTimeoutMs, onLockTimeoutMs)
                ModeRow(stringResource(R.string.app_lock_timeout_5m), 300_000L, lockTimeoutMs, onLockTimeoutMs)
            }
        }
    }
    Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_start_test_capture)) }
    Button(onClick = onOpenFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_open_media_folder)) }
    Text(stringResource(R.string.home_note))
}
