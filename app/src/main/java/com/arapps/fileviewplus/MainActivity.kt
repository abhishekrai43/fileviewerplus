// File: app/src/main/java/com/arapps/fileviewplus/MainActivity.kt
package com.arapps.fileviewplus

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.arapps.fileviewplus.notifications.FirebaseTokenLogger
import com.arapps.fileviewplus.settings.ThemeSettings
import com.arapps.fileviewplus.ui.FileViewApp
import com.arapps.fileviewplus.ui.components.SplashScreen
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

        // edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeFlow = ThemeSettings.getThemeFlow(applicationContext)
            val isDarkMode by themeFlow.collectAsState(initial = false)

            // status bar appearance
            SideEffect {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkMode
            }

            FileFlowPlusTheme(darkTheme = isDarkMode) {
                // showSplash toggles the overlay
                var showSplash by remember { mutableStateOf(true) }

                // Block back navigation while splash overlay is visible
                BackHandler(enabled = showSplash) {
                    // intentionally ignore back presses while splash is visible
                }

                // Render the real app (this starts any work immediately)
                Box(modifier = Modifier.fillMaxSize()) {
                    // Pass a flag so inner app can suppress its own legacy splash
                    FileViewApp(
                        isDarkMode = isDarkMode,
                        suppressInnerSplash = showSplash, // <-- NEW: tells inner app not to show its own splash
                        onToggleTheme = { enabled ->
                            lifecycleScope.launch {
                                ThemeSettings.setDarkMode(applicationContext, enabled)
                            }
                        }
                    )

                    // Overlay the splash above the app while showSplash == true.
                    if (showSplash) {
                        SplashScreen(
                            onFinish = { showSplash = false },
                            title = "FileFlow Plus",
                            scanningMessages = listOf(
                                "Loading filesystem",
                                "Preparing storage access",
                                "Checking permissions"
                            ),
                            minDisplayMillis = 1200L
                        )
                    }
                }
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
            recreate()
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
                e.printStackTrace()
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
