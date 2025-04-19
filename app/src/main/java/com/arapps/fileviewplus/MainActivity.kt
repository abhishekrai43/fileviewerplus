// File: com/arapps/fileviewplus/MainActivity.kt

package com.arapps.fileviewplus

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.arapps.fileviewplus.notifications.FirebaseTokenLogger
import com.arapps.fileviewplus.settings.ThemeSettings
import com.arapps.fileviewplus.ui.FileViewApp
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.launch

private const val UPDATE_REQUEST_CODE = 1001

class MainActivity : ComponentActivity() {

    private var permissionPreviouslyDenied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        FirebaseTokenLogger.logToken()
        checkAndRequestStoragePermission()
        checkForAppUpdate()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeFlow = ThemeSettings.getThemeFlow(applicationContext)
            val isDarkMode by themeFlow.collectAsState(initial = false)

            SideEffect {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkMode
            }

            FileFlowPlusTheme(darkTheme = isDarkMode) {
                FileViewApp(
                    isDarkMode = isDarkMode,
                    onToggleTheme = { enabled ->
                        lifecycleScope.launch {
                            ThemeSettings.setDarkMode(applicationContext, enabled)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            permissionPreviouslyDenied &&
            Environment.isExternalStorageManager()
        ) {
            recreate() // Relaunch if permission granted from settings
        }
    }

    private fun checkAndRequestStoragePermission() {
        permissionPreviouslyDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !Environment.isExternalStorageManager()
        } else {
            false
        }

        if (permissionPreviouslyDenied) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log permission intent failure
            }
        }
    }

    private fun checkForAppUpdate() {
        val updateManager = AppUpdateManagerFactory.create(this)
        val infoTask = updateManager.appUpdateInfo

        infoTask.addOnSuccessListener { updateInfo ->
            if (
                updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                updateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                updateManager.startUpdateFlow(
                    updateInfo,
                    this,
                    AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
                )
            }
        }.addOnFailureListener {
            it.printStackTrace()
        }
    }
}
