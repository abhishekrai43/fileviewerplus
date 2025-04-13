package com.arapps.fileviewplus.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.screens.openFileSafely
import java.io.File

@Composable
fun SearchResultItem(file: File, onOpen: (File) -> Unit, onProtectedClick: (File) -> Unit) {
    val isProtected = !file.canRead()
    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(if (!isProtected) Modifier.clickable { onOpen(file) } else Modifier.clickable { onProtectedClick(file) })

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isProtected) 0.dp else 2.dp,
        color = if (isProtected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isProtected) Icons.Default.Lock else Icons.Default.Image,
                    contentDescription = null,
                    tint = if (isProtected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                file.absolutePath,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (isProtected) {
                Text(
                    "Folder protected by Android. Please use system file explorer or grant full access.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
