package com.example.fileviewplus

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fileviewplus.logic.FileScanner
import com.example.fileviewplus.logic.NavigationState
import com.example.fileviewplus.logic.StorageStats
import com.example.fileviewplus.model.FileNode
import com.example.fileviewplus.ui.components.BottomBarActions
import com.example.fileviewplus.ui.components.FileActionsMenu
import com.example.fileviewplus.ui.components.FilePreview
import com.example.fileviewplus.ui.components.StorageUsageBar
import com.example.fileviewplus.ui.theme.FileViewPlusTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileViewPlusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewApp() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission()) }
    var fileStructure by remember { mutableStateOf<List<FileNode.Category>>(emptyList()) }
    var nav by remember { mutableStateOf(NavigationState()) }
    var isLoading by remember { mutableStateOf(false) }

    // Trigger scan when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true

            // 👇 Give Compose time to show loader
            delay(300)

            fileStructure = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
            isLoading = false
        }
    }

    // 🔒 Show permission screen if access not granted
    if (!hasPermission) {
        RequestPermissionScreen {
            openPermissionSettings(context)
        }
        return
    }

    // 📊 Calculate stats after scan
    val stats = remember(fileStructure) {
        StorageStats.calculateStats(fileStructure)
    }

    // 🧱 Root layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 🔝 App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Storage Usage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // 🔄 Show loader during scanning
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning files...", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            // ✅ Show results once done
            StorageUsageBar(stats)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when {
                    nav.day != null -> FileListView(nav.day!!) { nav = nav.goBack() }
                    nav.month != null -> DayListView(nav.month!!) { nav = nav.copy(day = it) }
                    nav.category != null -> MonthListView(nav.category!!) { nav = nav.copy(month = it) }
                    else -> CategoryListView(fileStructure) { nav = nav.copy(category = it) }
                }
            }

            BottomBarActions()
            ServerControlPanel()

        }
    }
}



@Composable
fun CategoryListView(categories: List<FileNode.Category>, onSelect: (FileNode.Category) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(categories) { cat ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSelect(cat) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun MonthListView(category: FileNode.Category, onSelect: (FileNode.Month) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(category.months) { month ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSelect(month) }
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "📂 ${month.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun DayListView(month: FileNode.Month, onSelect: (FileNode.Day) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(month.days) { day ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSelect(day) }
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "📅 ${day.name} (${day.files.size} files)",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun FileListView(day: FileNode.Day, onBack: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(day.files) { file ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    FilePreview(file)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = file.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)
                    )
                    FileActionsMenu(file)
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
@Composable
fun ServerControlPanel() {
    val context = LocalContext.current
    var serverStarted by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }

    if (!serverStarted) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            onClick = {
                com.example.fileviewplus.server.HttpFileServer.start()
                ipAddress = getLocalIpAddress()
                serverStarted = true
            }
        ) {
            Text("Start File Sharing Server")
        }
    } else {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            text = "🌐 Visit: http://$ipAddress:8080 from your PC",
            style = MaterialTheme.typography.bodyLarge
        )
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
    } catch (_: Exception) {}
    return "localhost"
}
