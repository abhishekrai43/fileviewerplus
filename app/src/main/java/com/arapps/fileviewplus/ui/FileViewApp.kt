package com.arapps.fileviewplus.ui


import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETE_MANUAL_REMIND
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.logic.FileScanner
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.model.NavigationState
import com.arapps.fileviewplus.ui.screens.*
import com.arapps.fileviewplus.utils.findActivity
import com.arapps.fileviewplus.viewer.AudioViewer
import com.arapps.fileviewplus.viewer.PlaybackController
import com.arapps.fileviewplus.viewer.ViewerRouter
import com.arapps.fileviewplus.ui.components.AudioMiniPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("RememberReturnType")
@Composable
fun FileViewApp(
    isDarkMode: Boolean,
    suppressInnerSplash: Boolean = false,
    onToggleTheme: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // keep a local used value to avoid an 'unused parameter' warning while preserving the API
    // (parameter intentionally unused in this scope)

    val hasPermission by remember { mutableStateOf(checkStoragePermission()) }
    var fileStructure by remember { mutableStateOf<List<FileNode.Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val nav = remember { mutableStateOf(NavigationState()) }

    fun removeFileFromStructure(path: String, structure: List<FileNode.Category>): List<FileNode.Category> {
        if (structure.isEmpty()) return structure
        return structure.mapNotNull { category ->
            val newYears = category.years.mapNotNull { year ->
                val newMonths = year.months.mapNotNull { month ->
                    val newDays = month.days.mapNotNull { day ->
                        val newFiles = day.files.filter { it.path != path }
                        if (newFiles.isEmpty()) null else day.copy(files = newFiles)
                    }
                    if (newDays.isEmpty()) null else month.copy(days = newDays)
                }
                if (newMonths.isEmpty()) null else year.copy(months = newMonths)
            }
            if (newYears.isEmpty()) null else category.copy(years = newYears)
        }
    }

    fun refreshFiles(force: Boolean = false) {
        isLoading = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (force || FileScanner.shouldScan(context)) {
                        FileScanner.scanAndCache(context)
                    } else {
                        FileScanner.loadFromCache(context)
                    }
                } catch (t: Throwable) {
                    listOf<FileNode.Category>()
                }
            }
            fileStructure = result
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val path = intent.getStringExtra(EXTRA_DELETED_PATH) ?: return
                val manualRemind = intent.getBooleanExtra(EXTRA_DELETE_MANUAL_REMIND, false)

                val newStructure = removeFileFromStructure(path, fileStructure)
                if (newStructure != fileStructure) {
                    fileStructure = newStructure
                }

                if (manualRemind) {
                    Toast.makeText(
                        context,
                        "Android restricted deletion. Please delete manually: $path",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Deleted: ${File(path).name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    // Watch MediaStore for changes so newly created photos/audio/videos are picked up promptly
    DisposableEffect(hasPermission) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val resolver = context.contentResolver
            val handler = Handler(Looper.getMainLooper())
            val debounceDelay = 1000L
            val refreshRunnable = Runnable { refreshFiles(force = true) }
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    // Debounce rapid MediaStore events — cancel previous and schedule a single refresh
                    try {
                        handler.removeCallbacks(refreshRunnable)
                        handler.postDelayed(refreshRunnable, debounceDelay)
                    } catch (_: Exception) {
                        // fallback to immediate refresh if handler fails
                        refreshFiles(force = true)
                    }
                }
            }

            // Register observers for images, video, audio and general files
            try {
                resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
            } catch (e: Exception) {
                // ignore - some devices may restrict observers
            }

            onDispose {
                try {
                    resolver.unregisterContentObserver(observer)
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) refreshFiles()
    }

    BackHandler(enabled = nav.value.isInSubScreen()) {
        nav.value = nav.value.goBack()
    }
    BackHandler(enabled = nav.value.showFileTypeExplorer || nav.value.showVault || nav.value.vaultFolder != null) {
        nav.value = NavigationState()
    }

    if (!hasPermission) {
        RequestPermissionScreen { openPermissionSettings(context) }
        return
    }

    if (nav.value.viewerFile != null) {
        val requested = nav.value.viewerFile!!
        val fileOnDisk = File(requested.path)
        if (!fileOnDisk.exists()) {
            fileStructure = removeFileFromStructure(requested.path, fileStructure)
            Toast.makeText(context, "File no longer exists: ${requested.name}", Toast.LENGTH_SHORT).show()
            nav.value = nav.value.copy(viewerFile = null, viewerIsVault = false)
        } else {
            // If the requested file is an audio file, play inline via PlaybackController instead of showing full-screen viewer
            if (requested.type == FileNode.FileType.Audio) {
                try {
                    PlaybackController.play(requested)
                } catch (_: Exception) {
                    // fallback to in-app overlay if playback controller fails
                    AudioViewer(fileNode = requested, isVault = nav.value.viewerIsVault) {
                        nav.value = nav.value.copy(viewerFile = null, viewerIsVault = false)
                    }
                }
                nav.value = nav.value.copy(viewerFile = null, viewerIsVault = false)
            } else {
                val activity = context.findActivity()
                ViewerRouter.openFile(activity ?: context, requested, nav.value.viewerIsVault)
                nav.value = nav.value.copy(viewerFile = null, viewerIsVault = false)
            }
        }
    }

    when {
        nav.value.day != null -> FileListScreen(nav.value.day!!) {
            nav.value = nav.value.goBack()
        }
        nav.value.month != null -> DayListScreen(
            nav.value.month!!,
            onSelect = { nav.value = nav.value.copy(day = it) },
            onBack = { nav.value = nav.value.copy(month = null) }
        )
        nav.value.year != null -> MonthListScreen(
            nav.value.year!!,
            onSelect = { nav.value = nav.value.copy(month = it) },
            onBack = { nav.value = nav.value.copy(year = null) }
        )
        nav.value.category != null -> YearListScreen(
            nav.value.category!!,
            onYearSelected = { nav.value = nav.value.copy(year = it) }
        )
        nav.value.showFileTypeExplorer -> FileTypeExplorerScreen(categories = fileStructure)
        nav.value.vaultFolder != null -> VaultFolderScreen(
            folder = nav.value.vaultFolder!!,
            onBack = { nav.value = nav.value.copy(vaultFolder = null) }
        )
        nav.value.showVault -> VaultScreen(
            onBack = { nav.value = NavigationState() },
            onOpenFolder = { nav.value = nav.value.copy(vaultFolder = it) },
            initialShowNotes = nav.value.showVaultNotes
        )
        else -> CategoryListScreen(
            fileStructure,
            { nav.value = nav.value.copy(category = it) },
            { nav.value = nav.value.copy(showFileTypeExplorer = true) },
            {
                Toast.makeText(context, "Toggle view not implemented", Toast.LENGTH_SHORT).show()
            },
            { nav.value = NavigationState() },
            isDarkMode,
            onToggleTheme,
            { nav.value = nav.value.copy(showVault = true) },
            nav,
            { refreshFiles(force = true) },
            isLoading
        )
    }

    // Global inline audio mini-player (plays whenever PlaybackController.active is set)
    val activeAudio by PlaybackController.active
    activeAudio?.let { node ->
        AudioMiniPlayer(fileNode = node, autoPlay = true, onClose = { PlaybackController.stop() })
    }
}

fun checkStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true
}

fun openPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open permission settings", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Permission needed only on Android 11+", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RequestPermissionScreen(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "To organize your files, FileFlow Plus needs full storage access.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrantClick) {
            Text("Grant Permission")
        }
    }
}

private fun NavigationState.isInSubScreen(): Boolean {
    return day != null || month != null || category != null
}
