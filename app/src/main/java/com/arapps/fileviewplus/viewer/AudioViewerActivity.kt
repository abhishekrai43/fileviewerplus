package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsControllerCompat
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import com.arapps.fileviewplus.settings.ThemeSettings
import java.io.File

class AudioViewerActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_FROM_VAULT = "fromVault"

        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean = false) {
            val intent = Intent(context, AudioViewerActivity::class.java).apply {
                putExtra(EXTRA_PATH, fileNode.path)
                putExtra(EXTRA_FROM_VAULT, fromVault)
            }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolve FileNode from intent
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Cannot open audio", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val node = FileNode.fromFile(File(path))
        val fromVault = intent.getBooleanExtra(EXTRA_FROM_VAULT, false)

        // Set Compose content and read theme inside composable context
        setContent {
            val themeFlow = ThemeSettings.getThemeFlow(applicationContext)
            val isDarkMode by themeFlow.collectAsState(initial = false)

            // Keep status bar icons readable when theme changes
            androidx.compose.runtime.SideEffect {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkMode
            }

            FileFlowPlusTheme(darkTheme = isDarkMode) {
                AudioViewer(fileNode = node, isVault = fromVault) {
                    finish()
                }
            }
        }
    }
}
