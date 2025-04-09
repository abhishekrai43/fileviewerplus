package com.arapps.fileviewplus

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.arapps.fileviewplus.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewApp() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission()) }
    var fileStructure by remember { mutableStateOf<List<FileNode.Category>>(emptyList()) }
    val nav = remember { mutableStateOf(NavigationState()) }
    var isLoading by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val result = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                withContext(Dispatchers.Main) {
                    fileStructure = result
                    isLoading = false
                }
            }
        }
    }

    if (!hasPermission) {
        RequestPermissionScreen {
            openPermissionSettings(context)
        }
        return
    }

    if (isLoading) {
        ScanningOverlay()
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                onSafClick = {
                    nav.value = nav.value.copy(saf = true)
                    scope.launch { drawerState.close() }
                },
                onShareClick = {
                    shareApp(context)
                    scope.launch { drawerState.close() }
                },
                onServerClick = {
                    nav.value = nav.value.copy(startServer = true)
                    scope.launch { drawerState.close() }
                }
            )
        },
        modifier = Modifier.fillMaxHeight().width(280.dp)
    ) {
        Scaffold(
            topBar = {
                FileViewTopAppBar(onDrawerClick = { scope.launch { drawerState.open() } })
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when {
                    nav.value.day != null -> FileListScreen(nav.value.day!!) { nav.value = nav.value.goBack() }
                    nav.value.month != null -> DayListScreen(nav.value.month!!, onSelect = {
                        nav.value = nav.value.copy(day = it)
                    }, onBack = {
                        nav.value = nav.value.copy(month = null)
                    })
                    nav.value.category != null -> MonthListScreen(nav.value.category!!, onSelect = {
                        nav.value = nav.value.copy(month = it)
                    }, onBack = {
                        nav.value = nav.value.copy(category = null)
                    })
                    nav.value.saf -> SafExplorerScreen(
                        safRoot = SafStorage.getUri(context) ?: run {
                            Toast.makeText(context, "SAF folder not selected", Toast.LENGTH_SHORT).show()
                            nav.value = nav.value.copy(saf = false)
                            return@Box
                        },
                        onExit = { nav.value = nav.value.copy(saf = false) }
                    )
                    nav.value.startServer -> ServerControlPanel().also {
                        nav.value = nav.value.copy(startServer = false)
                    }
                    else -> CategoryListScreen(
                        categories = fileStructure,
                        onSelect = { category -> nav.value = nav.value.copy(category = category) },
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                }
            }
        }
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
