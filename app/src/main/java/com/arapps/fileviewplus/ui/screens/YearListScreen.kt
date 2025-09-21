package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
import com.arapps.fileviewplus.ui.components.GalleryDialog
import com.arapps.fileviewplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.viewer.ImageViewerActivity.Companion.ACTION_FILE_DELETED
import java.io.File


@SuppressLint("NewApi")
@Composable
fun YearListScreen(
    category: FileNode.Category,
    onYearSelected: (FileNode.Year) -> Unit
) {
    // Mutable list of years so we can update counts on delete
    val years = remember { mutableStateListOf<FileNode.Year>().apply { addAll(category.years) } }
    val context = androidx.compose.ui.platform.LocalContext.current

    // flat-list toggle + gallery state
    var showFlatFiles by rememberSaveable { mutableStateOf(false) }
    val allYearFiles = remember { mutableStateListOf<FileNode>().apply { addAll(years.flatMap { y -> y.months.flatMap { m -> m.days.flatMap { d -> d.files } } }) } }
    fun rebuildFlatFromYears(list: List<FileNode.Year>) {
        val flat = list.flatMap { y -> y.months.flatMap { m -> m.days.flatMap { d -> d.files } } }
        allYearFiles.clear()
        allYearFiles.addAll(flat)
    }

    var galleryOpen by rememberSaveable { mutableStateOf(false) }
    var galleryStartIndex by rememberSaveable { mutableStateOf(0) }

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

                // rebuild flat list from updated years
                rebuildFlatFromYears(updatedYears)
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

    // Ensure the Year screen respects the app theme background (was showing as always dark
    // because it didn't set a background and inherited a dark parent in some navigation cases).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "\uD83D\uDCC1 ${category.name}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            IconButton(onClick = { showFlatFiles = !showFlatFiles }) {
                Icon(imageVector = if (showFlatFiles) Icons.Filled.Folder else Icons.AutoMirrored.Filled.List, contentDescription = "Toggle view")
            }
        }

        // Year screen: keep layout focused on years only (no storage summary here)
        Spacer(modifier = Modifier.height(4.dp))

        LazyVerticalGrid(
             columns = GridCells.Fixed(2),
             modifier = Modifier.fillMaxSize(),
             verticalArrangement = Arrangement.spacedBy(12.dp),
             horizontalArrangement = Arrangement.spacedBy(12.dp)
         ) {
            if (!showFlatFiles) {
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
            } else {
                items(allYearFiles, key = { it.path }) { file ->
                    val idx = allYearFiles.indexOf(file)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable {
                                galleryStartIndex = idx
                                galleryOpen = true
                            },
                        elevation = CardDefaults.cardElevation(3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            FilePreviewThumbnail(
                                file = File(file.path),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                            ),
                                        )
                            )

                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = file.name,
                                        // Use a readable color on top of the darkened image overlay. Keep white
                                        // to ensure contrast regardless of theme (overlay is dark).
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${file.size / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen gallery dialog moved outside the LazyVerticalGrid
        if (galleryOpen) {
            GalleryDialog(files = allYearFiles, startIndex = galleryStartIndex, onClose = { galleryOpen = false })
        }
    }
 }
