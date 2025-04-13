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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ⚠️ CHECK AND LAUNCH SETTINGS IF STORAGE ACCESS IS NOT GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            // DO NOT return or block setContent — let user come back and app will resume normally
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
}
