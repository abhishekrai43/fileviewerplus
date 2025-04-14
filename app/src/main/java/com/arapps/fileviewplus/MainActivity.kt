package com.arapps.fileviewplus

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
import com.arapps.fileviewplus.settings.ThemeSettings
import com.arapps.fileviewplus.ui.FileViewApp
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var permissionPreviouslyDenied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionPreviouslyDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !Environment.isExternalStorageManager()
        } else {
            false
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permissionPreviouslyDenied) {
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

        // 🔁 Recreate the activity once if user just granted permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            permissionPreviouslyDenied &&
            Environment.isExternalStorageManager()
        ) {
            recreate()
        }
    }
}
