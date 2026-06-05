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
import de.eberhardt.unlockcapture.settings.AppSettings
import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode
import de.eberhardt.unlockcapture.ui.components.CompactModeRow
import de.eberhardt.unlockcapture.ui.components.ModeRow
import de.eberhardt.unlockcapture.ui.components.RequirementCard
import de.eberhardt.unlockcapture.ui.components.SettingsGroupTitle
import de.eberhardt.unlockcapture.ui.components.adaptiveActionButtonWidth

@Composable
internal fun SetupScreen(
    cameraOk: Boolean,
    notificationsOk: Boolean,
    adminOk: Boolean,
    mediaOk: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.setup_intro))

    RequirementCard(
        title = stringResource(R.string.setup_req_camera_title),
        ok = cameraOk,
        description = stringResource(R.string.setup_req_camera_desc),
        actionLabel = stringResource(R.string.action_grant_permissions),
        onAction = onRequestPermissions,
    )
    RequirementCard(
        title = stringResource(R.string.setup_req_notifications_title),
        ok = notificationsOk,
        description = stringResource(R.string.setup_req_notifications_desc),
        actionLabel = stringResource(R.string.action_open_notification_settings),
        onAction = onOpenNotificationSettings,
    )
    RequirementCard(
        title = stringResource(R.string.setup_req_media_title),
        ok = mediaOk,
        description = stringResource(R.string.setup_req_media_desc),
        actionLabel = stringResource(R.string.action_grant_permissions),
        onAction = onRequestPermissions,
    )
    RequirementCard(
        title = stringResource(R.string.setup_req_admin_title),
        ok = adminOk,
        description = stringResource(R.string.setup_req_admin_desc),
        actionLabel = stringResource(R.string.action_enable_device_admin),
        onAction = onOpenDeviceAdmin,
    )

    Button(onClick = onRefresh, modifier = Modifier.adaptiveActionButtonWidth()) { Text(stringResource(R.string.action_recheck)) }
}

@Composable
internal fun HomeScreen(
    settings: AppSettings,
    notificationsOk: Boolean,
    appLockAvailable: Boolean,
    actions: HomeScreenActions,
) {
    Text(stringResource(R.string.home_status_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.home_status_body))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SettingsGroupTitle(stringResource(R.string.capture_mode_title))
            ModeRow(stringResource(R.string.capture_mode_photo), CaptureMode.PHOTO, settings.captureMode, actions.onMode)
            ModeRow(stringResource(R.string.capture_mode_video), CaptureMode.VIDEO_4_SECONDS, settings.captureMode, actions.onMode)
        }
    }
    if (settings.captureMode == CaptureMode.VIDEO_4_SECONDS) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsGroupTitle(stringResource(R.string.video_duration_title))
                Text(
                    pluralStringResource(R.plurals.video_duration_seconds, settings.videoDurationSeconds, settings.videoDurationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = settings.videoDurationSeconds.toFloat(),
                    onValueChange = { actions.onVideoDurationSeconds(it.toInt()) },
                    valueRange = 3f..20f,
                    steps = 16,
                )
                Text(stringResource(R.string.video_duration_hint), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsGroupTitle(stringResource(R.string.unlock_logging_title))
            ModeRow(stringResource(R.string.unlock_logging_failed_only), UnlockLoggingMode.FAILED_ONLY, settings.unlockLoggingMode, actions.onUnlockLoggingMode)
            ModeRow(stringResource(R.string.unlock_logging_all), UnlockLoggingMode.ALL, settings.unlockLoggingMode, actions.onUnlockLoggingMode)
            Text(stringResource(R.string.unlock_logging_hint), style = MaterialTheme.typography.labelSmall)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsGroupTitle(stringResource(R.string.notifications_group_title))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.failed_unlock_warning_setting_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = settings.failedUnlockWarningEnabled, onCheckedChange = actions.onFailedUnlockWarningEnabled)
            }
            Text(stringResource(R.string.failed_unlock_warning_setting_desc), style = MaterialTheme.typography.labelSmall)
            if (!notificationsOk) {
                Button(onClick = actions.onRequestNotifications, modifier = Modifier.adaptiveActionButtonWidth()) {
                    Text(stringResource(R.string.action_allow_notifications))
                }
            }
        }
    }
    if (appLockAvailable) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsGroupTitle(stringResource(R.string.app_lock_title))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.app_lock_enabled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = settings.lockEnabled, onCheckedChange = actions.onLockEnabled)
                }
                if (settings.lockEnabled) {
                    Text(stringResource(R.string.app_lock_timeout_title), style = MaterialTheme.typography.labelSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        CompactModeRow(stringResource(R.string.app_lock_timeout_immediate), 0L, settings.lockTimeoutMs, actions.onLockTimeoutMs)
                        CompactModeRow(stringResource(R.string.app_lock_timeout_30s), 30_000L, settings.lockTimeoutMs, actions.onLockTimeoutMs)
                        CompactModeRow(stringResource(R.string.app_lock_timeout_5m), 300_000L, settings.lockTimeoutMs, actions.onLockTimeoutMs)
                    }
                }
            }
        }
    }
    Button(onClick = actions.onTest, modifier = Modifier.adaptiveActionButtonWidth()) { Text(stringResource(R.string.action_start_test_capture)) }
    Button(onClick = actions.onOpenFolder, modifier = Modifier.adaptiveActionButtonWidth()) { Text(stringResource(R.string.action_open_media_folder)) }
    Text(stringResource(R.string.home_note), style = MaterialTheme.typography.labelSmall)
}
