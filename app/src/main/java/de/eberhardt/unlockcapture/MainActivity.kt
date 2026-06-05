package de.eberhardt.unlockcapture

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import de.eberhardt.unlockcapture.ui.AppScreen
import de.eberhardt.unlockcapture.util.AppLog
import de.eberhardt.unlockcapture.util.PermissionUtils

class MainActivity : AppCompatActivity() {
    private lateinit var mainViewModel: MainViewModel
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            mainViewModel.refreshPermissions()
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            mainViewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        AppLog.i("MainActivity", "onCreate() intentAction=${intent?.action}")
        setContent {
            AppScreen(
                mainViewModel = mainViewModel,
                onRequestPermissions = {
                    permissionLauncher.launch(PermissionUtils.runtimePermissions())
                },
                onOpenDeviceAdmin = { startActivity(PermissionUtils.deviceAdminIntent(this)) },
                onOpenNotificationSettings = { startActivity(PermissionUtils.appNotificationSettingsIntent(this)) },
                onRequestNotifications = {
                    val permissions = PermissionUtils.notificationPermissions()
                    if (permissions.isNotEmpty()) {
                        notificationPermissionLauncher.launch(permissions)
                    } else {
                        mainViewModel.refreshPermissions()
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.i("MainActivity", "onResume()")
        mainViewModel.onAppResumed()
    }
}
