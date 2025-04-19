package com.arapps.fileviewplus.ui


import ScanningOverlay
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.logic.FileScanner
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.model.NavigationState
import com.arapps.fileviewplus.ui.components.FileViewTopAppBar
import com.arapps.fileviewplus.ui.screens.*
import com.arapps.fileviewplus.viewer.ViewerRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewApp(isDarkMode: Boolean, onToggleTheme: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(checkStoragePermission()) }
    var fileStructure by remember { mutableStateOf<List<FileNode.Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val nav = remember { mutableStateOf(NavigationState()) }


    fun refreshFiles() {
        isLoading = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (FileScanner.shouldScan(context)) {
                    FileScanner.scanAndCache(context)
                } else {
                    FileScanner.loadFromCache(context)
                }
            }
            fileStructure = result
            isLoading = false
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

    if (isLoading) {
        ScanningOverlay()
        return
    }
    if (nav.value.viewerFile != null) {
        ViewerRouter.openFile(context, nav.value.viewerFile!!, nav.value.viewerIsVault)
        return
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
            onOpenFolder = { nav.value = nav.value.copy(vaultFolder = it) }
        )

        else -> CategoryListScreen(
            categories = fileStructure,
            onSelect = { nav.value = nav.value.copy(category = it) },
            onSearch = { nav.value = nav.value.copy(showFileTypeExplorer = true) },
            onToggleView = {
                Toast.makeText(context, "Toggle view not implemented", Toast.LENGTH_SHORT).show()
            },
            onGoHome = { nav.value = NavigationState() },
            isDarkMode = isDarkMode,
            onToggleTheme = onToggleTheme,
            onVaultClick = { nav.value = nav.value.copy(showVault = true) },
            nav = nav
        )
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
