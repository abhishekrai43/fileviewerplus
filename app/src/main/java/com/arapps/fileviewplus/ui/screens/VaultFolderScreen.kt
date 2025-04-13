package com.arapps.fileviewplus.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.ui.components.vault.VaultFileCard
import com.arapps.fileviewplus.utils.importFileToVault
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFolderScreen(folder: File, onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(folder.listFiles()?.toList() ?: emptyList()) }
    var showImportBanner by remember { mutableStateOf(false) }

    val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> importFileToVault(context, uri, folder) }
            files = folder.listFiles()?.toList() ?: emptyList()
            showImportBanner = true
        }
    }

    // Auto-hide banner after 5 seconds
    LaunchedEffect(showImportBanner) {
        if (showImportBanner) {
            delay(5000)
            showImportBanner = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importFilesLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Files")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {

            if (showImportBanner) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "✅ Your file is secure in the Vault.\nDelete the original if you don't want others to see it.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (files.isEmpty()) {
                Text("Folder is empty.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(files, key = { it.absolutePath }) { file ->
                        VaultFileCard(
                            file = file,
                            onFileChanged = {
                                files = folder.listFiles()?.toList() ?: emptyList()
                            }
                        )
                    }
                }
            }
        }
    }
}
