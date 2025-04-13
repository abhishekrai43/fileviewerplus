package com.arapps.fileviewplus.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.*
import com.arapps.fileviewplus.utils.FileAnalytics
import com.arapps.fileviewplus.utils.getLocalIpAddress
import com.arapps.fileviewplus.utils.isOnWifi
import com.arapps.ftpserver.FtpServerController
import com.arapps.fileviewplus.server.HttpFileServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    categories: List<FileNode.Category>,
    onSelect: (FileNode.Category) -> Unit,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onGoHome: () -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onVaultClick: () -> Unit

) {
    val context = LocalContext.current
    val useFtp = remember { mutableStateOf(true) }
    val isServerRunning = remember { mutableStateOf(false) }
    val showDialog = remember { mutableStateOf(false) }
    val ipAddress = remember { mutableStateOf("") }
    val protocol = remember { mutableStateOf("FTP") }

    val stats = remember(categories) { StorageStats.calculateStats(categories) }

    val allFiles = remember(categories) {
        categories.flatMap { cat ->
            cat.years.flatMap { it.months }
                .flatMap { it.days }
                .flatMap { it.files }
        }
    }

    var insights by remember { mutableStateOf<List<FileAnalytics.FileInsight>>(emptyList()) }
    LaunchedEffect(allFiles) {
        withContext(Dispatchers.IO) {
            insights = allFiles.map {
                FileAnalytics.FileInsight(
                    file = it,
                    size = it.length(),
                    lastModified = it.lastModified()
                )
            }
        }
    }

    val oldFiles = remember(insights) { FileAnalytics.getOldFiles(insights, 180) }
    val largeFiles = remember(insights) { FileAnalytics.getLargeFiles(insights, 200) }

    var showFilteredList by remember { mutableStateOf(false) }
    var filteredFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var filteredTitle by remember { mutableStateOf("Filtered Files") }

    val suggestions = listOfNotNull(
        oldFiles.takeIf { it.isNotEmpty() }?.let {
            "\uD83D\uDCC5 ${it.size} file${if (it.size > 1) "s" else ""} not opened in 6 months" to {
                filteredTitle = "Old Files"
                filteredFiles = oldFiles.map { it.file }
                showFilteredList = true
            }
        },
        largeFiles.takeIf { it.isNotEmpty() }?.let {
            "\uD83D\uDC00 ${it.size} large file${if (it.size > 1) "s" else ""} (>200MB)" to {
                filteredTitle = "Large Files"
                filteredFiles = largeFiles.map { it.file }
                showFilteredList = true
            }
        }
    )

    var suggestionIndex by remember { mutableStateOf(0) }

    LaunchedEffect(suggestions) {
        while (true) {
            delay(5000)
            if (suggestions.isNotEmpty()) {
                suggestionIndex = (suggestionIndex + 1) % suggestions.size
            }
        }
    }

    if (showFilteredList) {
        FilteredFileListScreen(
            oldFiles = oldFiles.map { it.file },
            largeFiles = largeFiles.map { it.file },
            onBack = { showFilteredList = false }
        )
        return
    }

    Scaffold(
        topBar = {
            FileViewTopAppBar(
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                onGoHome = onGoHome
            )
        },
        bottomBar = {
            BottomBarActions(
                modifier = Modifier.navigationBarsPadding(),
                onSearch = onSearch,
                onToggleView = onToggleView,
                onToggleServer = {
                    if (isServerRunning.value) {
                        HttpFileServer.stop()
                        FtpServerController.stop()
                        isServerRunning.value = false
                        Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
                    } else {
                        if (!isOnWifi(context)) {
                            Toast.makeText(context, "Connect to Wi-Fi to start server", Toast.LENGTH_SHORT).show()
                            return@BottomBarActions
                        }
                        val ip = getLocalIpAddress()
                        ipAddress.value = ip
                        if (useFtp.value) {
                            FtpServerController.start(context)
                            protocol.value = "FTP"
                        } else {
                            HttpFileServer.start()
                            protocol.value = "HTTP"
                        }
                        isServerRunning.value = true
                        showDialog.value = true
                        Toast.makeText(context, "Server started", Toast.LENGTH_SHORT).show()
                    }
                },
                isServerRunning = isServerRunning.value,
                serverTypeToggle = {
                    Switch(
                        checked = useFtp.value,
                        onCheckedChange = { useFtp.value = it },
                        modifier = Modifier.size(ButtonDefaults.MinHeight)
                    )
                },
                onShareApp = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Try FileFlow Plus")
                        putExtra(Intent.EXTRA_TEXT, "Check out this awesome file manager:\nhttps://play.google.com/store/apps/details?id=com.arapps.fileviewplus")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                },
                onVaultClick = onVaultClick
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                StorageUsageBar(stats = stats)

                if (suggestions.isNotEmpty()) {
                    val (text, action) = suggestions[suggestionIndex]
                    SmartSuggestionsPanel(
                        suggestionText = text,
                        onClick = action
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { cat ->
                    val allFiles = cat.years.flatMap { it.months }.flatMap { it.days }.flatMap { it.files }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
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

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text("Server Started ✅") },
            text = {
                val port = if (protocol.value == "FTP") 2121 else 8080
                Text(
                    "Protocol: ${protocol.value}\n" +
                            "IP Address: ${ipAddress.value}\n" +
                            "Port: $port\n\n" +
                            "No username/password needed.\n" +
                            "If using FTP use a FTP client like Filezilla\n\n" +
                            "For HTTP type ${ipAddress.value}:8080 in your Chrome or Safari address bar.\n\n" +
                            "Current protocol being used is ${protocol.value}"
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}
