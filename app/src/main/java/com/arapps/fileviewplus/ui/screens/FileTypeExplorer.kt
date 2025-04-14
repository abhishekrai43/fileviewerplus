package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.arapps.fileviewplus.ui.screens.FileCategory




@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileTypeExplorerScreen(
    categories: List<FileNode.Category>
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? ComponentActivity
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Toast.makeText(context, "Access granted!", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedType by remember { mutableStateOf<FileCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val allFiles = remember(categories) {
        categories
            .flatMap { it.years }
            .flatMap { it.months }
            .flatMap { it.days }
            .flatMap { it.files }
    }

    val filteredFiles = remember(allFiles, selectedType, searchQuery) {
        allFiles.filter { file ->
            val matchesType = selectedType == null || getFileCategory(file.name) == selectedType
            val matchesQuery = file.name.contains(searchQuery, ignoreCase = true)
            matchesType && matchesQuery
        }
    }.groupBy { getFileCategory(it.name) }

    Scaffold { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp)) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files...") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FileCategory.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(type.label, maxLines = 1) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn {
                filteredFiles.forEach { (type, files) ->
                    item {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    items(files) { file ->
                        val isProtected = !File(file.path).canRead()

                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(file.name, modifier = Modifier.weight(1f))
                                    if (isProtected) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Protected",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Column {
                                    Text(file.path, style = MaterialTheme.typography.labelSmall)
                                    if (isProtected) {
                                        Text(
                                            "Protected. Tap to grant access.",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(type.icon, contentDescription = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isProtected) {
                                        launcher.launch(Uri.fromFile(File(file.parent)))
                                    } else {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".Fileprovider",
                                            File(file.path)
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, getMimeType(file.name))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open with"))
                                    }
                                }
                        )
                    }
                }
            }
        }

    }

}
private fun getFileCategory(name: String): FileCategory {
    val lower = name.lowercase()
    return when {
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp").any { lower.endsWith(it) } -> FileCategory.IMAGE
        listOf(".mp4", ".mkv", ".avi", ".mov").any { lower.endsWith(it) } -> FileCategory.VIDEO
        listOf(".mp3", ".wav", ".ogg", ".m4a").any { lower.endsWith(it) } -> FileCategory.AUDIO
        listOf(".pdf", ".txt", ".doc", ".docx", ".ppt", ".pptx").any { lower.endsWith(it) } -> FileCategory.DOCUMENT
        else -> FileCategory.OTHER
    }
}
private fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "")
    return android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}
