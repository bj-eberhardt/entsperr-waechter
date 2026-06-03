package de.eberhardt.unlockcapture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import de.eberhardt.unlockcapture.browse.CapturedArtifact
import de.eberhardt.unlockcapture.browse.MediaStoreBrowser
import de.eberhardt.unlockcapture.audit.AuditLog
import de.eberhardt.unlockcapture.audit.AuditLogVerification
import de.eberhardt.unlockcapture.capture.CaptureTrigger
import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.settings.SettingsRepository
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode
import de.eberhardt.unlockcapture.util.AppLog
import de.eberhardt.unlockcapture.util.PermissionUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import de.eberhardt.unlockcapture.capture.CaptureForegroundService
import de.eberhardt.unlockcapture.security.BiometricGate
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import de.eberhardt.unlockcapture.integrity.IntegrityStore
import android.content.Context

class MainActivity : AppCompatActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private var refreshState: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("MainActivity", "onCreate() intentAction=${intent?.action}")
        setContent {
            AppScreen(
                onRequestPermissions = {
                    permissionLauncher.launch(PermissionUtils.runtimePermissions())
                },
                onOpenDeviceAdmin = { startActivity(PermissionUtils.deviceAdminIntent(this)) },
                onOpenNotificationSettings = { startActivity(PermissionUtils.appNotificationSettingsIntent(this)) },
                registerRefresh = { refreshState = it }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.i("MainActivity", "onResume()")
        refreshState?.invoke()
    }
}

private enum class Tab { SETTINGS, BROWSE, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    onRequestPermissions: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    registerRefresh: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.applicationContext) }
    val mode by settings.captureMode.collectAsState(initial = CaptureMode.PHOTO)
    val videoDurationSeconds by settings.videoDurationSeconds.collectAsState(initial = 4)
    val unlockLoggingMode by settings.unlockLoggingMode.collectAsState(initial = UnlockLoggingMode.FAILED_ONLY)
    val lockEnabled by settings.lockEnabled.collectAsState(initial = false)
    val lockTimeoutMs by settings.lockTimeoutMs.collectAsState(initial = 0L)
    val lastAuthElapsedMs by settings.lastAuthElapsedMs.collectAsState(initial = 0L)
    val scope = rememberCoroutineScope()

    var cameraOk by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(false) }
    var adminOk by remember { mutableStateOf(false) }
    var mediaOk by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.SETTINGS) }
    var showAbout by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var gateInProgress by remember { mutableStateOf(false) }

    fun refresh() {
        cameraOk = PermissionUtils.hasCamera(context)
        notificationsOk = PermissionUtils.hasNotifications(context)
        adminOk = PermissionUtils.isDeviceAdminActive(context)
        mediaOk = PermissionUtils.hasMediaRead(context)
        AppLog.i("MainActivity", "status camera=$cameraOk notifications=$notificationsOk media=$mediaOk admin=$adminOk mode=$mode")
    }

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
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    suspend fun ensureUnlocked(): Boolean {
        if (!lockEnabled) return true
        val timeout = lockTimeoutMs
        val now = SystemClock.elapsedRealtime()
        val last = lastAuthElapsedMs
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
            subtitle = context.getString(R.string.app_lock_prompt_subtitle)
        )
        gateInProgress = false
        if (ok) {
            settings.setLastAuthElapsedMs(SystemClock.elapsedRealtime())
        } else {
            snackbarHostState.showSnackbar(context.getString(R.string.app_lock_failed))
        }
        return ok
    }

    LaunchedEffect(Unit) {
        registerRefresh { refresh() }
        refresh()
    }

    LaunchedEffect(lockEnabled) {
        if (lockEnabled) {
            ensureUnlocked()
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
                                modifier = Modifier.size(26.dp)
                            )
                            Text(stringResource(R.string.app_name))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAbout = true }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_info_details),
                                contentDescription = stringResource(R.string.action_about_privacy)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS,
                        onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_manage), contentDescription = stringResource(R.string.tab_settings)) },
                        label = { Text(stringResource(R.string.tab_settings)) }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.BROWSE,
                        onClick = {
                            scope.launch {
                                if (ensureUnlocked()) tab = Tab.BROWSE
                            }
                        },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_gallery), contentDescription = stringResource(R.string.tab_browse)) },
                        label = { Text(stringResource(R.string.tab_browse)) }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HISTORY,
                        onClick = {
                            scope.launch {
                                if (ensureUnlocked()) tab = Tab.HISTORY
                            }
                        },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_recent_history), contentDescription = stringResource(R.string.tab_history)) },
                        label = { Text(stringResource(R.string.tab_history)) }
                    )
                }
            }
        ) { padding ->
            Surface(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                when (tab) {
                    Tab.SETTINGS -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (cameraOk && notificationsOk && adminOk) {
                                HomeScreen(
                                    mode = mode,
                                    videoDurationSeconds = videoDurationSeconds,
                                    onVideoDurationSeconds = { scope.launch { settings.setVideoDurationSeconds(it) } },
                                    unlockLoggingMode = unlockLoggingMode,
                                    onUnlockLoggingMode = { scope.launch { settings.setUnlockLoggingMode(it) } },
                                    lockEnabled = lockEnabled,
                                    onLockEnabled = { scope.launch { settings.setLockEnabled(it) } },
                                    lockTimeoutMs = lockTimeoutMs,
                                    onLockTimeoutMs = { scope.launch { settings.setLockTimeoutMs(it) } },
                                    onMode = { scope.launch { settings.setCaptureMode(it) } },
                                    onTest = { CaptureTrigger.start(context, CaptureReason.MANUAL_TEST) },
                                    onOpenFolder = { openAppMediaFolder(context as Activity) }
                                )
                            } else {
                                SetupScreen(
                                    cameraOk = cameraOk,
                                    notificationsOk = notificationsOk,
                                    adminOk = adminOk,
                                    mediaOk = mediaOk,
                                    onRequestPermissions = onRequestPermissions,
                                    onOpenDeviceAdmin = onOpenDeviceAdmin,
                                    onOpenNotificationSettings = onOpenNotificationSettings,
                                    onRefresh = { refresh() }
                                )
                            }
                        }
                    }
                    Tab.BROWSE -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BrowseScreen(
                                canBrowse = mediaOk,
                                onRequestPermissions = onRequestPermissions
                            )
                        }
                    }
                    Tab.HISTORY -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HistoryScreen()
                        }
                    }
                }
            }
        }

        if (showAbout) {
            AboutDialog(
                onDismiss = { showAbout = false }
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
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun BrowseScreen(
    canBrowse: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val loaded = MediaStoreBrowser.load(context.applicationContext)
                val presentUris = loaded.map { it.uri.toString() }.toHashSet()
                val presentEntries = withContext(Dispatchers.IO) {
                    loaded.map { a ->
                        val missing = runCatching {
                            context.contentResolver.openInputStream(a.uri)?.use { true } ?: false
                        }.getOrDefault(false).not()
                        BrowseEntry.Present(a, missing)
                    }
                }

                val missingEntries = withContext(Dispatchers.IO) {
                    IntegrityStore.list(context.applicationContext).filter { rec ->
                        if (presentUris.contains(rec.uri)) return@filter false
                        val uri = runCatching { Uri.parse(rec.uri) }.getOrNull() ?: return@filter true
                        runCatching { context.contentResolver.openInputStream(uri)?.use { true } ?: false }
                            .getOrDefault(false)
                            .not()
                    }.map { BrowseEntry.Missing(it) }
                }
                entries = (presentEntries + missingEntries).sortedByDescending { it.tsMs }
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(canBrowse) {
        if (canBrowse) refresh()
    }

    if (!canBrowse) {
        Text(stringResource(R.string.browse_need_permissions))
        Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_grant_permissions))
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { refresh() }, modifier = Modifier.weight(1f)) {
            Text(if (loading) stringResource(R.string.loading_ellipsis) else stringResource(R.string.action_refresh))
        }
    }

    if (error != null) {
        Text(stringResource(R.string.browse_error_prefix, error ?: ""))
    }

    if (entries.isEmpty() && !loading) {
        Text(stringResource(R.string.browse_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.browse_empty_body))
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(entries.size) { index ->
            when (val e = entries[index]) {
                is BrowseEntry.Present -> {
                    val item = e.item
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        item.uri,
                                        if (item.type.name == "VIDEO") "video/*" else "image/*"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(viewIntent) }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArtifactThumbnail(item = item)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(dateFormat.format(Date(item.dateAddedSeconds * 1000)), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (item.type.name == "VIDEO") stringResource(R.string.artifact_type_video) else stringResource(R.string.artifact_type_photo),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (e.missing) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_dialog_alert),
                                    contentDescription = stringResource(R.string.artifact_missing),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                is BrowseEntry.Missing -> {
                    val rec = e.record
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_dialog_alert),
                                contentDescription = stringResource(R.string.artifact_missing),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.artifact_missing), style = MaterialTheme.typography.titleMedium)
                                Text(dateFormat.format(Date(rec.tsMs)), style = MaterialTheme.typography.bodyMedium)
                                Text(rec.uri, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class BrowseEntry(open val tsMs: Long) {
    data class Present(val item: CapturedArtifact, val missing: Boolean) : BrowseEntry(item.dateAddedSeconds * 1000L)
    data class Missing(val record: de.eberhardt.unlockcapture.integrity.IntegrityStore.Record) : BrowseEntry(record.tsMs)
}

@Composable
private fun ArtifactThumbnail(item: CapturedArtifact) {
    val context = LocalContext.current
    val image = produceState<android.graphics.Bitmap?>(initialValue = null, key1 = item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(item.uri, Size(192, 192), null)
                } else {
                    @Suppress("DEPRECATION")
                    when (item.type.name) {
                        "VIDEO" -> MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            item.id,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                        else -> MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver,
                            item.id,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null
                        )
                    }
                }
            }.getOrNull()
        }
    }.value

    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .height(64.dp)
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            painter = painterResource(if (item.type.name == "VIDEO") android.R.drawable.ic_media_play else android.R.drawable.ic_menu_report_image),
            contentDescription = null
        )
    }
}

@Composable
private fun SetupScreen(
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
private fun HomeScreen(
    mode: CaptureMode,
    videoDurationSeconds: Int,
    onVideoDurationSeconds: (Int) -> Unit,
    unlockLoggingMode: UnlockLoggingMode,
    onUnlockLoggingMode: (UnlockLoggingMode) -> Unit,
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

@Composable
private fun ModeRow(label: String, value: CaptureMode, selected: CaptureMode, onMode: (CaptureMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onMode(value) })
        Text(label)
    }
}

@Composable
private fun ModeRow(label: String, value: UnlockLoggingMode, selected: UnlockLoggingMode, onMode: (UnlockLoggingMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onMode(value) })
        Text(label)
    }
}

@Composable
private fun ModeRow(label: String, value: Long, selected: Long, onMode: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onMode(value) })
        Text(label)
    }
}

@Composable
private fun StatusCard(title: String, ok: Boolean, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
                Text(
                stringResource(
                    R.string.status_format,
                    title,
                    if (ok) stringResource(R.string.status_ok) else stringResource(R.string.status_missing)
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(description)
        }
    }
}

@Composable
private fun RequirementCard(
    title: String,
    ok: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                stringResource(
                    R.string.status_format,
                    title,
                    if (ok) stringResource(R.string.status_ok) else stringResource(R.string.status_missing)
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(description)
            if (!ok) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AuditLog.Entry>>(emptyList()) }
    var verification by remember { mutableStateOf<AuditLogVerification>(AuditLogVerification.Empty) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) { AuditLog.readAndVerify(context.applicationContext) }
                items = result.entries.filter { it.type == "UNLOCK" }.sortedByDescending { it.tsMs }
                verification = result.verification
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { refresh() }, modifier = Modifier.weight(1f)) {
            Text(if (loading) stringResource(R.string.loading_ellipsis) else stringResource(R.string.action_refresh))
        }
    }

    if (verification is AuditLogVerification.Tampered) {
        Text(stringResource(R.string.history_tamper_warning), color = MaterialTheme.colorScheme.error)
    }

    if (error != null) {
        Text(stringResource(R.string.history_error_prefix, error ?: ""))
    }

    if (items.isEmpty() && !loading) {
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.history_empty_body))
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(items.size) { index ->
            val entry = items[index]
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFail = entry.result == "FAIL"
                    val iconRes =
                        if (isFail) android.R.drawable.ic_delete else android.R.drawable.checkbox_on_background
                    val tint =
                        if (isFail) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    Icon(painterResource(iconRes), contentDescription = null, tint = tint)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Text(historyMessage(context, entry), style = MaterialTheme.typography.bodyMedium)
                        Text(entry.isoTime, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun openAppMediaFolder(activity: Activity) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("content://media/external/images/media")
        type = "image/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { activity.startActivity(intent) }
        .onFailure { activity.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
}

private fun historyMessage(context: Context, entry: AuditLog.Entry): String {
    return when (entry.eventKey) {
        AuditLog.EVENT_UNLOCK_FAILED -> context.getString(R.string.history_event_unlock_failed)
        AuditLog.EVENT_UNLOCK_SUCCESS -> context.getString(R.string.history_event_unlock_success)
        else -> entry.message
    }
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
