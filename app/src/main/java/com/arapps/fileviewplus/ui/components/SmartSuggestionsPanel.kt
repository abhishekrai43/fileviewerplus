package com.arapps.fileviewplus.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.model.FileNode
import java.io.File

/**
 * SmartSuggestionsPanel
 *
 * Shows a dynamic list of FileNodes. Reacts to delete broadcasts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSuggestionsPanel(
    initialSuggestions: List<FileNode> = emptyList(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val suggestions = remember { mutableStateListOf<FileNode>().apply { addAll(initialSuggestions) } }

    // Listen for delete broadcasts
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val deletedPath = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                suggestions.removeAll { it.path == deletedPath }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧠 Smart Suggestions",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            if (suggestions.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    tonalElevation = 2.dp,
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = suggestions.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }

        // Panel
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onClick() },
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                if (suggestions.isEmpty()) {
                    Text("No suggestions right now", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 72.dp * 5),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(suggestions.take(5), key = { it.path }) { fileNode ->
                            SuggestionRow(fileNode = fileNode)
                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overload for single-text suggestion (legacy API).
 * Creates a fake FileNode to render one panel.
 */
@Composable
fun SmartSuggestionsPanel(
    suggestionText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "🧠 Smart Suggestions",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = suggestionText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        }
    }
}

/**
 * Wrapper to show multiple suggestion categories (like old + large files).
 */
@Composable
fun SmartSuggestionsPanelGroup(
    oldFilesLabel: String,
    onOldFilesClick: () -> Unit,
    largeFilesLabel: String,
    onLargeFilesClick: () -> Unit
) {
    Column {
        SmartSuggestionsPanel(suggestionText = oldFilesLabel, onClick = onOldFilesClick)
        SmartSuggestionsPanel(suggestionText = largeFilesLabel, onClick = onLargeFilesClick)
    }
}

@Composable
private fun SuggestionRow(fileNode: FileNode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbnailPainter = rememberAsyncImagePainter(model = File(fileNode.path))
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp
        ) {
            Image(
                painter = thumbnailPainter,
                contentDescription = fileNode.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(fileNode.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(fileNode.extension.uppercase(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
