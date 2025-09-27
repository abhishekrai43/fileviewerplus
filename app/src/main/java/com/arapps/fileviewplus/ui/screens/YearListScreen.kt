package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
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
import com.arapps.fileviewplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
import com.arapps.fileviewplus.viewer.ViewerRouter
import com.arapps.fileviewplus.viewer.ImageViewerActivity.Companion.ACTION_FILE_DELETED
import java.io.File
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.copyFilesToVaultAsync
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.ui.components.vault.EnterPinDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YearListScreen(
    category: FileNode.Category,
    onYearSelected: (FileNode.Year) -> Unit
) {
    // Mutable list of years so we can update counts on delete
    val years = remember { mutableStateListOf<FileNode.Year>().apply { addAll(category.years) } }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Selection state (support multi-select when viewing flat file list)
    val selected = remember { mutableStateListOf<String>() }
    var showSelectionToolbar by remember { mutableStateOf(false) }
    var showEnterPin by remember { mutableStateOf(false) }
    var showChooseVaultFolder by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val vaultRoot = File(context.filesDir, ".vault").apply { mkdirs() }

    // flat-list toggle + gallery state
    var showFlatFiles by rememberSaveable { mutableStateOf(false) }
    val allYearFiles = remember { mutableStateListOf<FileNode>().apply { addAll(years.flatMap { y -> y.months.flatMap { m -> m.days.flatMap { d -> d.files } } }) } }
    fun rebuildFlatFromYears(list: List<FileNode.Year>) {
        val flat = list.flatMap { y -> y.months.flatMap { m -> m.days.flatMap { d -> d.files } } }
        allYearFiles.clear()
        allYearFiles.addAll(flat)
    }

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

    // helper to attempt delete originals after copy
    suspend fun attemptDeleteOriginals() {
        selected.toList().forEach { p ->
            try {
                val node = FileNode.fromFile(File(p))
                when (val res = DeletionManager.deleteFile(context, node)) {
                    DeletionManager.DeleteResult.Deleted -> {
                        // removed via broadcast elsewhere
                    }
                    is DeletionManager.DeleteResult.NeedUserGrant -> {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Cannot delete automatically: permission needed for $p", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    is DeletionManager.DeleteResult.Failed -> {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Delete failed: ${res.reason}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
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
                    val isSelected = selected.contains(file.path)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable {
                                if (selected.isNotEmpty()) {
                                    if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                    showSelectionToolbar = selected.isNotEmpty()
                                } else {
                                    ViewerRouter.openFile(context, file, fromVault = false)
                                }
                            }.combinedClickable(onClick = {
                                if (selected.isNotEmpty()) {
                                    if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                    showSelectionToolbar = selected.isNotEmpty()
                                } else {
                                    ViewerRouter.openFile(context, file, fromVault = false)
                                }
                            }, onLongClick = {
                                if (!isSelected) selected.add(file.path)
                                showSelectionToolbar = true
                            }),
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

        // Selection toolbar at top (show when user has selected files)
        if (showSelectionToolbar && selected.isNotEmpty()) {
            TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = { IconButton(onClick = { selected.clear(); showSelectionToolbar = false }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection") } },
                actions = {
                    IconButton(onClick = {
                        // Share first selected
                        val uris = selected.mapNotNull { p ->
                            try { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", File(p)) } catch (_: Exception) { null }
                        }
                        if (uris.isNotEmpty()) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uris.first())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share files"))
                        }
                    }) { Icon(imageVector = Icons.Filled.Share, contentDescription = "Share") }

                    IconButton(onClick = { showEnterPin = true }) { Icon(imageVector = Icons.Filled.Lock, contentDescription = "Move to Vault") }

                    IconButton(onClick = {
                        uiScope.launch {
                            attemptDeleteOriginals()
                            selected.clear(); showSelectionToolbar = false
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            )
        }

        if (showEnterPin) {
            EnterPinDialog(onPinEntered = { pin -> if (pin == getStoredPin(context)) { showEnterPin = false; showChooseVaultFolder = true } else android.widget.Toast.makeText(context, "Incorrect PIN", android.widget.Toast.LENGTH_SHORT).show() }, onDismiss = { showEnterPin = false }, onForgotPin = {})
        }

        if (showChooseVaultFolder) {
            val folders = vaultRoot.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            var selectedFolder by remember { mutableStateOf(folders.firstOrNull() ?: "") }
            AlertDialog(onDismissRequest = { showChooseVaultFolder = false }, title = { Text("Select Vault Folder") }, text = {
                Column { folders.forEach { name -> Row(modifier = Modifier.fillMaxWidth().clickable { selectedFolder = name }.padding(8.dp)) { RadioButton(selected = selectedFolder == name, onClick = { selectedFolder = name }); Spacer(Modifier.width(8.dp)); Text(name) } } }
            }, confirmButton = {
                TextButton(onClick = {
                    val dest = if (selectedFolder.isBlank()) vaultRoot else File(vaultRoot, selectedFolder)
                    uiScope.launch {
                        val copied = copyFilesToVaultAsync(context, selected.toList(), dest)
                        withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "${copied.size} file(s) copied to vault", android.widget.Toast.LENGTH_SHORT).show() }
                        attemptDeleteOriginals()
                        selected.clear(); showSelectionToolbar = false; showChooseVaultFolder = false
                    }
                }) { Text("Move") }
            }, dismissButton = { TextButton(onClick = { showChooseVaultFolder = false }) { Text("Cancel") } })
        }
    }
 }
