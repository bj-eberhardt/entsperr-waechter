package de.eberhardt.unlockcapture.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.eberhardt.unlockcapture.MainViewModel
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.capture.CaptureForegroundService
import de.eberhardt.unlockcapture.capture.CaptureTrigger
import de.eberhardt.unlockcapture.security.BiometricGate
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.ui.browse.BrowseScreen
import de.eberhardt.unlockcapture.ui.history.HistoryScreen
import de.eberhardt.unlockcapture.ui.settings.HomeScreen
import de.eberhardt.unlockcapture.ui.settings.HomeScreenActions
import de.eberhardt.unlockcapture.ui.settings.SetupScreen
import kotlinx.coroutines.launch

private enum class Tab { SETTINGS, BROWSE, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScreen(
    mainViewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val permissionState = uiState.permissionState
    val appLockAvailable = uiState.appLockAvailable
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.SETTINGS) }
    var showAbout by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var gateInProgress by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action != CaptureForegroundService.ACTION_CAPTURE_STATUS) return
                val reasonName = intent.getStringExtra(CaptureForegroundService.EXTRA_REASON) ?: return
                if (reasonName != CaptureReason.MANUAL_TEST.name) return
                val state = intent.getStringExtra(CaptureForegroundService.EXTRA_STATE) ?: return
                val success = intent.getBooleanExtra(CaptureForegroundService.EXTRA_SUCCESS, true)
                val error = intent.getStringExtra(CaptureForegroundService.EXTRA_ERROR)
                scope.launch {
                    when (state) {
                        CaptureForegroundService.STATE_STARTED -> snackbarHostState.showSnackbar(ctx.getString(R.string.test_capture_running))
                        CaptureForegroundService.STATE_FINISHED -> {
                            val msg = if (success) {
                                ctx.getString(R.string.test_capture_finished)
                            } else {
                                ctx.getString(R.string.test_capture_finished_error, formatCaptureError(ctx, error))
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(CaptureForegroundService.ACTION_CAPTURE_STATUS)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    suspend fun ensureUnlocked(): Boolean {
        if (!settings.lockEnabled) return true
        if (!appLockAvailable) {
            mainViewModel.setLockEnabled(false)
            return true
        }
        val timeout = settings.lockTimeoutMs
        val now = SystemClock.elapsedRealtime()
        val last = settings.lastAuthElapsedMs
        val needAuth = when (timeout) {
            0L -> true
            else -> (last <= 0L) || (now - last > timeout)
        }
        if (!needAuth) return true
        val activity = context as? FragmentActivity ?: return false
        gateInProgress = true
        val ok = BiometricGate.authenticate(
            activity = activity,
            title = context.getString(R.string.app_lock_prompt_title),
            subtitle = context.getString(R.string.app_lock_prompt_subtitle),
        )
        gateInProgress = false
        if (ok) {
            mainViewModel.setLastAuthElapsedMs(SystemClock.elapsedRealtime())
        } else {
            snackbarHostState.showSnackbar(context.getString(R.string.app_lock_failed))
        }
        return ok
    }

    LaunchedEffect(Unit) {
        mainViewModel.refreshPermissions()
    }

    LaunchedEffect(settings.lockEnabled, appLockAvailable) {
        if (settings.lockEnabled && appLockAvailable) {
            ensureUnlocked()
        } else if (settings.lockEnabled) {
            mainViewModel.setLockEnabled(false)
        }
    }

    MaterialTheme {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(
                                painter = painterResource(R.drawable.ic_appbar_logo),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                            Text(stringResource(R.string.app_name))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAbout = true }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_info_details),
                                contentDescription = stringResource(R.string.action_about_privacy),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS,
                        onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_manage), contentDescription = stringResource(R.string.tab_settings)) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.BROWSE,
                        onClick = {
                            scope.launch {
                                if (ensureUnlocked()) tab = Tab.BROWSE
                            }
                        },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_gallery), contentDescription = stringResource(R.string.tab_browse)) },
                        label = { Text(stringResource(R.string.tab_browse)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HISTORY,
                        onClick = {
                            scope.launch {
                                if (ensureUnlocked()) tab = Tab.HISTORY
                            }
                        },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_recent_history), contentDescription = stringResource(R.string.tab_history)) },
                        label = { Text(stringResource(R.string.tab_history)) },
                    )
                }
            },
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (tab) {
                    Tab.SETTINGS -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (permissionState.cameraOk && permissionState.adminOk) {
                                HomeScreen(
                                    settings = settings,
                                    notificationsOk = permissionState.notificationsOk,
                                    appLockAvailable = appLockAvailable,
                                    actions = HomeScreenActions(
                                        onVideoDurationSeconds = mainViewModel::setVideoDurationSeconds,
                                        onUnlockLoggingMode = mainViewModel::setUnlockLoggingMode,
                                        onFailedUnlockWarningEnabled = mainViewModel::setFailedUnlockWarningEnabled,
                                        onRequestNotifications = onRequestNotifications,
                                        onLockEnabled = mainViewModel::setLockEnabled,
                                        onLockTimeoutMs = mainViewModel::setLockTimeoutMs,
                                        onMode = mainViewModel::setCaptureMode,
                                        onTest = { CaptureTrigger.start(context, CaptureReason.MANUAL_TEST) },
                                        onOpenFolder = { openAppMediaFolder(context as Activity) },
                                    ),
                                )
                            } else {
                                SetupScreen(
                                    cameraOk = permissionState.cameraOk,
                                    notificationsOk = permissionState.notificationsOk,
                                    adminOk = permissionState.adminOk,
                                    mediaOk = permissionState.mediaOk,
                                    onRequestPermissions = onRequestPermissions,
                                    onOpenDeviceAdmin = onOpenDeviceAdmin,
                                    onOpenNotificationSettings = onOpenNotificationSettings,
                                    onRefresh = { mainViewModel.refreshPermissions() },
                                )
                            }
                        }
                    }
                    Tab.BROWSE -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            BrowseScreen(
                                canBrowse = permissionState.mediaOk,
                                onRequestPermissions = onRequestPermissions,
                            )
                        }
                    }
                    Tab.HISTORY -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HistoryScreen()
                        }
                    }
                }
            }
        }

        if (showAbout) {
            AboutDialog(
                onDismiss = { showAbout = false },
            )
        }
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = { Text(stringResource(R.string.about_body)) },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun openAppMediaFolder(activity: Activity) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse("content://media/external/images/media"), "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { activity.startActivity(intent) }
        .onFailure { activity.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
}

private fun formatCaptureError(context: Context, error: String?): String {
    if (error.isNullOrBlank()) return "-"
    return when {
        error == CaptureForegroundService.ERROR_CAMERA_PERMISSION_MISSING -> {
            context.getString(R.string.capture_error_camera_permission_missing)
        }
        error.startsWith(CaptureForegroundService.ERROR_VIDEO_FINALIZE_PREFIX) -> {
            val code = error.removePrefix(CaptureForegroundService.ERROR_VIDEO_FINALIZE_PREFIX)
            context.getString(R.string.capture_error_video_finalize, code)
        }
        else -> error
    }
}
