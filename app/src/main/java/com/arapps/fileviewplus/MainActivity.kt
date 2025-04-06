package com.arapps.fileviewplus

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.logic.FileScanner
import com.arapps.fileviewplus.logic.NavigationState
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.SplashScreen
import com.arapps.fileviewplus.ui.components.BottomBarActions
import com.arapps.fileviewplus.ui.components.FileActionsMenu
import com.arapps.fileviewplus.ui.components.FilePreview
import com.arapps.fileviewplus.ui.components.StorageUsageBar
import com.arapps.fileviewplus.ui.theme.FileViewPlusTheme
import com.arapps.fileviewplus.utils.NotificationUtils
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.utils.isOnWifi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileViewPlusTheme {
                var showSplash by remember { mutableStateOf(true) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSplash) {
                        SplashScreen(onFinish = { showSplash = false })
                    } else {
                        FileViewApp()
                    }
                }
            }
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
                "To organize your files, FileViewPlus needs full storage access.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrantClick) {
                Text("Grant Permission")
            }
        }
    }

    @Composable
    fun FileViewApp() {
        val context = LocalContext.current
        var hasPermission by remember { mutableStateOf(checkStoragePermission()) }
        var fileStructure by remember { mutableStateOf<List<FileNode.Category>>(emptyList()) }
        var nav by remember { mutableStateOf(NavigationState()) }
        var isLoading by remember { mutableStateOf(false) }
        BackHandler(enabled = nav.day != null || nav.month != null || nav.category != null) {
            nav = nav.goBack()
        }

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

        when {
            nav.day != null -> FileListScreen(nav.day!!) { nav = nav.goBack() }
            nav.month != null -> DayListScreen(
                nav.month!!,
                onSelect = { nav = nav.copy(day = it) },
                onBack = { nav = nav.copy(month = null) }
            )

            nav.category != null -> MonthListScreen(
                nav.category!!,
                onSelect = { nav = nav.copy(month = it) },
                onBack = { nav = nav.copy(category = null) }
            )

            else -> CategoryListScreen(fileStructure) { nav = nav.copy(category = it) }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CategoryListScreen(
        categories: List<FileNode.Category>,
        onSelect: (FileNode.Category) -> Unit
    ) {
        val stats = remember(categories) {
            StorageStats.calculateStats(categories)
        }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Storage Usage", fontWeight = FontWeight.Bold) })
            },
            bottomBar = {
                Column {
                    BottomBarActions()
                    ServerControlPanel()
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                StorageUsageBar(stats)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(categories) { cat ->
                        val allFiles = cat.months.flatMap { it.days }.flatMap { it.files }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onSelect(cat) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(cat.name, modifier = Modifier.weight(1f))
                                FolderActionsMenu(folderName = cat.name, files = allFiles)
                            }
                        }
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MonthListScreen(
        category: FileNode.Category,
        onSelect: (FileNode.Month) -> Unit,
        onBack: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("📁 ${category.name}") }, navigationIcon = {
                    IconButton(onClick = onBack) { Text("⬅") }
                })
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                items(category.months) { month ->
                    val allFiles = month.days.flatMap { it.files }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onSelect(month) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📂 ${month.name}", modifier = Modifier.weight(1f))
                            FolderActionsMenu(folderName = month.name, files = allFiles)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DayListScreen(month: FileNode.Month, onSelect: (FileNode.Day) -> Unit, onBack: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("🗓️ ${month.name}") }, navigationIcon = {
                    IconButton(onClick = onBack) { Text("⬅") }
                })
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                items(month.days) { day ->
                    val allFiles = day.files

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onSelect(day) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📅 ${day.name} (${day.files.size} files)",
                                modifier = Modifier.weight(1f)
                            )
                            FolderActionsMenu(folderName = day.name, files = allFiles)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FileListScreen(day: FileNode.Day, onBack: () -> Unit) {
        val context = LocalContext.current  // Get the current context

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("📄 ${day.name}") }, navigationIcon = {
                    IconButton(onClick = onBack) { Text("⬅") }
                })
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                items(day.files) { file ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { openFile(file, context) }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Add file preview here (if needed)
                            FilePreview(file)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(file.name, modifier = Modifier.weight(1f))

                            FileActionsMenu(file)
                        }
                    }
                }
            }
        }
    }

    fun openFile(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )  // Use FileProvider to create content URI
        val mimeType = context.contentResolver.getType(uri)  // Auto-detect the MIME type

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)  // Automatically set MIME type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  // Grant permission for reading the URI
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            // Handle case where no app can open the file (e.g., no PDF viewer)
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Unable to open permission settings", Toast.LENGTH_LONG)
                    .show()
            }
        } else {
            Toast.makeText(context, "Permission needed only on Android 11+", Toast.LENGTH_SHORT)
                .show()
        }
    }

    @Composable
    fun ServerControlPanel() {
        val context = LocalContext.current
        var serverStarted by remember { mutableStateOf(false) }
        var ipAddress by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding() // Prevents overlap with system nav
        ) {
            if (!serverStarted) {
                Button(
                    onClick = {
                        if (!isOnWifi(context)) {
                            Toast.makeText(context, "Please connect to Wi-Fi", Toast.LENGTH_LONG)
                                .show()
                            return@Button
                        }

                        com.arapps.fileviewplus.server.HttpFileServer.start()
                        val ip = getLocalIpAddress()
                        NotificationUtils.createNotificationChannel(context)
                        NotificationUtils.showServerRunningNotification(context, ip)

                        ipAddress = ip
                        serverStarted = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Local File Sharing Server")
                }
            } else {
                Text(
                    "🌐 Visit on PC: http://$ipAddress:8080",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(
                    onClick = {
                        com.arapps.fileviewplus.server.HttpFileServer.stop()
                        NotificationUtils.cancelServerNotification(context)

                        ipAddress = ""
                        serverStarted = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Server")
                }
            }

        }
    }


    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "localhost"
    }

    @Composable
    fun ScanningOverlay() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Add fun spinning emoji or larger progress
                Text("🔄", fontSize = MaterialTheme.typography.displaySmall.fontSize)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scanning files, please wait...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    @Composable
    fun FolderActionsMenu(
        folderName: String,
        files: List<File>,
        modifier: Modifier = Modifier
    ) {
        var expanded by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Box(modifier = modifier) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Zip & Share") },
                    onClick = {
                        expanded = false
                        CoroutineScope(Dispatchers.IO).launch {
                            val zipFile = ZipUtils.createZip(context, folderName, files)
                            ZipUtils.shareZip(context, zipFile)
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Properties") },
                    onClick = {
                        expanded = false
                        val totalSize = files.sumOf { it.length() }
                        val fileCount = files.size
                        val readableSize =
                            android.text.format.Formatter.formatShortFileSize(context, totalSize)

                        CoroutineScope(Dispatchers.Main).launch {
                            showPropertiesDialog(context, folderName, fileCount, readableSize)
                        }
                    }
                )
            }
        }
    }

    private fun showPropertiesDialog(
        context: Context,
        folderName: String,
        fileCount: Int,
        size: String
    ) {
        val message = """
        📁 Folder: $folderName
        📄 Files: $fileCount
        📦 Size: $size
    """.trimIndent()

        CoroutineScope(Dispatchers.Main).launch {
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Folder Properties")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}

