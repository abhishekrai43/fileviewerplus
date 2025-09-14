package com.arapps.fileviewplus.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
import com.arapps.fileviewplus.utils.findActivity
import com.arapps.fileviewplus.viewer.ViewerRouter
import java.io.File
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH


@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayListScreen(
    month: FileNode.Month,
    onSelect: (FileNode.Day) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFlatFiles by rememberSaveable { mutableStateOf(false) }

    // Keep mutable state lists so recomposition happens on delete
    val days = remember { mutableStateListOf<FileNode.Day>().apply { addAll(month.days) } }
    val allDayFiles = remember { mutableStateListOf<FileNode>().apply { addAll(month.days.flatMap { it.files }) } }

    // Listen for file deletion broadcasts
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                // Remove from flat list
                val removedFile = allDayFiles.firstOrNull { it.path == path }
                if (removedFile != null) {
                    allDayFiles.remove(removedFile)
                }
                // Remove from days list (recompute each day.files minus deleted file)
                val updated = days.map { d ->
                    d.copy(files = d.files.filterNot { it.path == path })
                }
                days.clear()
                days.addAll(updated)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${month.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("⬅") }
                },
                actions = {
                    TextButton(onClick = { showFlatFiles = !showFlatFiles }) {
                        Icon(
                            imageVector = if (showFlatFiles) Icons.Default.Folder else Icons.Default.List,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showFlatFiles) "Grouped View" else "View All Files")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!showFlatFiles) {
                items(days.sortedByDescending { it.name }, key = { it.name }) { day ->
                    val allFiles = day.files

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onSelect(day) },
                        elevation = CardDefaults.cardElevation(6.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${day.name}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Text(
                                        text = "${allFiles.size} files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                FolderActionsMenu(folderName = day.name, files = allFiles)
                            }
                        }
                    }
                }
            } else {
                items(allDayFiles, key = { it.path }) { file ->
                    val activity = context.findActivity()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clickable {
                                ViewerRouter.openFile(activity ?: context, file, fromVault = false)
                            },
                        elevation = CardDefaults.cardElevation(6.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(context)
                                        .data(File(file.path))
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                        )
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
