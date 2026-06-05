package de.eberhardt.unlockcapture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.eberhardt.unlockcapture.notify.FailedUnlockNotifier
import de.eberhardt.unlockcapture.settings.SettingsRepository
import de.eberhardt.unlockcapture.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionState(
    val cameraOk: Boolean = false,
    val notificationsOk: Boolean = false,
    val adminOk: Boolean = false,
    val mediaOk: Boolean = false,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settings = SettingsRepository(appContext)
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

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
}
