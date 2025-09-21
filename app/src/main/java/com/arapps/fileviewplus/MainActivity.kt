// File: app/src/main/java/com/arapps/fileviewplus/MainActivity.kt
package com.arapps.fileviewplus

import android.annotation.SuppressLint
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
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.util.Log
import androidx.work.WorkManager
import com.arapps.fileviewplus.data.NoteStore

private const val UPDATE_REQUEST_CODE = 1001

class MainActivity : ComponentActivity() {

    private var permissionPreviouslyDenied = false

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.google.firebase.FirebaseApp.initializeApp(this)
        FirebaseTokenLogger.logToken()
        checkAndRequestStoragePermission()
        checkForAppUpdate()

        // Diagnostic: check scheduled reminders and schedule a short test alarm in debug builds
        debugReminderState()

        // Check reminder-related permissions/settings (notifications and exact alarms)
        checkReminderPermissionsAndSettings()

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

    private fun debugReminderState() {
        try {
            val notes = NoteStore.getAllNotes(this)
            val alarmManager = getSystemService(AlarmManager::class.java)
            val wm = WorkManager.getInstance(applicationContext)

            for (note in notes) {
                val id = note.id
                val hasReminder = note.reminderAt != null
                val pendingIntent = try {
                    PendingIntent.getBroadcast(this, kotlin.math.abs(id.hashCode()), Intent(this, Class.forName("com.arapps.fileviewplus.receiver.NoteReminderReceiver")),
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                } catch (e: Exception) {
                    null
                }

                Log.d("ReminderDebug", "Note id=$id reminderAt=${note.reminderAt} hasReminder=$hasReminder pendingIntentExists=${pendingIntent != null}")

                try {
                    val future = wm.getWorkInfosForUniqueWork("note_reminder_$id")
                    val infos = future.get()
                    Log.d("ReminderDebug", "WorkManager infos for note_reminder_$id size=${infos.size}")
                } catch (e: Exception) {
                    Log.w("ReminderDebug", "Failed to query WorkManager for note_reminder_$id", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderDebug", "debugReminderState failed", e)
        }

        // NOTE: Removed automatic debug scheduling of a test reminder.
        // The function now only logs current reminder/work state and does not schedule anything.
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

    private fun checkReminderPermissionsAndSettings() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val notificationsEnabled = nm?.areNotificationsEnabled() ?: true
            Log.d("ReminderDebug", "Notifications enabled: $notificationsEnabled")

            if (!notificationsEnabled) {
                // Open app notification settings so user can enable notifications
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(AlarmManager::class.java)
                val canExact = try { alarmManager?.canScheduleExactAlarms() == true } catch (e: Exception) { false }
                Log.d("ReminderDebug", "canScheduleExactAlarms=$canExact")
                if (!canExact) {
                    // Open the system dialog where user may grant exact alarm scheduling
                    val intent = Intent("android.app.action.REQUEST_SCHEDULE_EXACT_ALARM").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ReminderDebug", "checkReminderPermissionsAndSettings failed", e)
        }
    }
}
