package com.arapps.fileviewplus.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.arapps.fileflowplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
import com.arapps.fileviewplus.utils.findActivity
import com.arapps.fileviewplus.viewer.ViewerRouter
import java.io.File
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthListScreen(
    year: FileNode.Year,
    onSelect: (FileNode.Month) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFlatFiles by rememberSaveable { mutableStateOf(false) }

    // Mutable state so deletions trigger recomposition
    val months = remember { mutableStateListOf<FileNode.Month>().apply { addAll(year.months) } }
    val allMonthFiles = remember { mutableStateListOf<FileNode>().apply { addAll(year.months.flatMap { it.days.flatMap { d -> d.files } }) } }

    // Helper to recompute flat list from months (call after months change)
    fun rebuildFlatFilesFromMonths(mList: List<FileNode.Month>) {
        val newFlat = mList.flatMap { m -> m.days.flatMap { d -> d.files } }
        allMonthFiles.clear()
        allMonthFiles.addAll(newFlat)
    }

    // Listen for delete broadcasts
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                Log.d("MonthListScreen", "Received delete broadcast for: $path")

                // Remove from flat file list if present
                val removed = allMonthFiles.firstOrNull { it.path == path }
                if (removed != null) {
                    allMonthFiles.remove(removed)
                }

                // Walk months -> days -> files and remove the file, dropping empty days and months
                val updatedMonths = months.mapNotNull { month ->
                    // For each day in month, remove the file and keep only non-empty days
                    val updatedDays = month.days.mapNotNull { day ->
                        val newFiles = day.files.filterNot { it.path == path }
                        if (newFiles.isNotEmpty()) day.copy(files = newFiles) else null
                    }
                    // Keep month only if it still has days
                    if (updatedDays.isNotEmpty()) month.copy(days = updatedDays) else null
                }

                // Apply new months -> this will trigger recomposition
                months.clear()
                months.addAll(updatedMonths)

                // Rebuild flat list from updated months (keeps counts consistent)
                rebuildFlatFilesFromMonths(updatedMonths)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📁 ${year.name}") },
                navigationIcon = { IconButton(onClick = onBack) { Text("⬅") } },
                actions = {
                    IconButton(onClick = { showFlatFiles = !showFlatFiles }) {
                        Icon(
                            imageVector = if (showFlatFiles) Icons.Default.Folder else Icons.Default.List,
                            contentDescription = "Toggle view"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!showFlatFiles) {
                items(months.sortedByDescending { it.name }, key = { it.name }) { month ->
                    val allFiles = month.days.flatMap { it.files }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onSelect(month) },
                        elevation = CardDefaults.cardElevation(3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📁 ${month.name}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            val fileCount = allFiles.size
                            val sizeText = StorageStats.formatSize(allFiles.sumOf { it.size })

                            Text(
                                text = "$fileCount files",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Box(modifier = Modifier.align(Alignment.End)) {
                                FolderActionsMenu(folderName = month.name, files = allFiles)
                            }
                        }
                    }
                }
            } else {
                items(allMonthFiles, key = { it.path }) { file ->
                    val activity = context.findActivity()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable {
                                ViewerRouter.openFile(activity ?: context, file, fromVault = false)
                            },
                        elevation = CardDefaults.cardElevation(3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilePreviewThumbnail(file = File(file.path))
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
