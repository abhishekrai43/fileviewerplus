package com.arapps.fileviewplus.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH


@Composable
fun YearListScreen(
    category: FileNode.Category,
    onYearSelected: (FileNode.Year) -> Unit
) {
    // Mutable list of years so we can update counts on delete
    val years = remember { mutableStateListOf<FileNode.Year>().apply { addAll(category.years) } }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Listen for delete broadcasts
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                android.util.Log.d("YearListScreen", "Received delete broadcast for: $path")

                // Walk the whole tree: remove the file, drop empty days/months/years
                val updatedYears = years.mapNotNull { year ->
                    val updatedMonths = year.months.mapNotNull { month ->
                        val updatedDays = month.days.mapNotNull { day ->
                            val newFiles = day.files.filterNot { it.path == path }
                            // keep the day only if it still has files
                            if (newFiles.isNotEmpty()) day.copy(files = newFiles) else null
                        }
                        // keep the month only if it still has days
                        if (updatedDays.isNotEmpty()) month.copy(days = updatedDays) else null
                    }
                    // keep the year only if it still has months
                    if (updatedMonths.isNotEmpty()) year.copy(months = updatedMonths) else null
                }

                // apply the new state
                years.clear()
                years.addAll(updatedYears)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "\uD83D\uDCC1 ${category.name}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(years.sortedByDescending { it.name.toIntOrNull() ?: 0 }, key = { it.name }) { year ->
                val allFiles = year.months.flatMap { it.days }.flatMap { it.files }
                val fileCount = allFiles.size
                val totalSize = StorageStats.formatSize(allFiles.sumOf { it.size })

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onYearSelected(year) },
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
                            text = "\uD83D\uDCC1 ${year.name}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Text(
                            text = "$fileCount files",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = totalSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Box(modifier = Modifier.align(Alignment.End)) {
                            FolderActionsMenu(folderName = year.name, files = allFiles)
                        }
                    }
                }
            }
        }
    }
}
