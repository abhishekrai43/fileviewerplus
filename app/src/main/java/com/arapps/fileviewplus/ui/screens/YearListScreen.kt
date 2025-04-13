package com.arapps.fileviewplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.ui.components.FolderActionsMenu

@Composable
fun YearListScreen(
    category: FileNode.Category,
    onYearSelected: (FileNode.Year) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Text(
            text = "📁 ${category.name}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(category.years.sortedByDescending { it.name.toIntOrNull() ?: 0 }) { year ->
                val allFiles = year.months.flatMap { it.days }.flatMap { it.files }
                val fileCount = allFiles.size
                val totalSize = StorageStats.formatSize(allFiles.sumOf { it.length() })

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
                            text = "📁 ${year.name}",
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
