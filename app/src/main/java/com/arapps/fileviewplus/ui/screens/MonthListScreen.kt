package com.arapps.fileviewplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FolderActionsMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthListScreen(
    year: FileNode.Year,
    onSelect: (FileNode.Month) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📁 ${year.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("⬅")
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
            items(year.months.sortedByDescending { it.name }) { month ->
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

                        val allFiles = month.days.flatMap { it.files }
                        val fileCount = allFiles.size
                        val sizeText = StorageStats.formatSize(allFiles.sumOf { it.length() })

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
        }
    }
}
