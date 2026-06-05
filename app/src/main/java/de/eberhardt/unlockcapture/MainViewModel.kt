package de.eberhardt.unlockcapture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.eberhardt.unlockcapture.notify.FailedUnlockNotifier
import de.eberhardt.unlockcapture.security.BiometricGate
import de.eberhardt.unlockcapture.settings.AppSettings
import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.SettingsRepository
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode
import de.eberhardt.unlockcapture.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val cameraOk: Boolean = false,
    val notificationsOk: Boolean = false,
    val adminOk: Boolean = false,
    val mediaOk: Boolean = false,
)

data class MainUiState(
    val settings: AppSettings = AppSettings(
        captureMode = CaptureMode.PHOTO,
        videoDurationSeconds = 4,
        unlockLoggingMode = UnlockLoggingMode.FAILED_ONLY,
        lockEnabled = false,
        lockTimeoutMs = 0L,
        lastAuthElapsedMs = 0L,
        failedUnlockWarningEnabled = false,
    ),
    val permissionState: PermissionState = PermissionState(),
    val appLockAvailable: Boolean = false,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settings = SettingsRepository(appContext)
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    val uiState: StateFlow<MainUiState> =
        combine(settings.appSettings, permissionState) { appSettings, permissions ->
            MainUiState(
                settings = appSettings,
                permissionState = permissions,
                appLockAvailable = BiometricGate.isAvailable(appContext),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(
                appLockAvailable = BiometricGate.isAvailable(appContext),
            ),
        )

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        _permissionState.value =
            PermissionState(
                cameraOk = PermissionUtils.hasCamera(appContext),
                notificationsOk = PermissionUtils.hasNotifications(appContext),
                adminOk = PermissionUtils.isDeviceAdminActive(appContext),
                mediaOk = PermissionUtils.hasMediaRead(appContext),
            )
    }

    fun onAppResumed() {
        refreshPermissions()
        FailedUnlockNotifier.cancel(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            settings.resetFailedUnlockWarningStats()
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        viewModelScope.launch { settings.setCaptureMode(mode) }
    }

    fun setVideoDurationSeconds(seconds: Int) {
        viewModelScope.launch { settings.setVideoDurationSeconds(seconds) }
    }

    fun setUnlockLoggingMode(mode: UnlockLoggingMode) {
        viewModelScope.launch { settings.setUnlockLoggingMode(mode) }
    }

    fun setFailedUnlockWarningEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setFailedUnlockWarningEnabled(enabled) }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockEnabled(enabled) }
    }

    fun setLockTimeoutMs(timeoutMs: Long) {
        viewModelScope.launch { settings.setLockTimeoutMs(timeoutMs) }
    }

    suspend fun setLastAuthElapsedMs(elapsedMs: Long) {
        settings.setLastAuthElapsedMs(elapsedMs)
    }
}
