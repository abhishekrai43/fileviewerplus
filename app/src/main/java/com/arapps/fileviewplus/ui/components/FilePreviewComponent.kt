package com.arapps.fileviewplus.ui.components
// NOTE: original package had a typo ('fileflowplus' vs 'fileviewplus') in older versions.
// Kept this package name to avoid breaking references; callers in the project import the
// public function `FilePreviewThumbnail` by fully qualified package where needed. If you
// prefer to move this into `com.arapps.fileviewplus.ui.components`, rename the package and
// update imports across the repo.

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.util.Locale

@Composable
fun FilePreviewThumbnail(file: File, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val ext = file.extension.lowercase(Locale.getDefault())
    when {
        ext.matches(Regex("jpg|jpeg|png|webp|bmp|gif", RegexOption.IGNORE_CASE)) -> {
            AsyncImage(
                model = file,
                contentDescription = contentDescription ?: file.name,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        ext.matches(Regex("mp4|mkv|webm|avi|mov", RegexOption.IGNORE_CASE)) -> {
            Box(modifier = modifier) {
                AsyncImage(
                    model = file,
                    contentDescription = contentDescription ?: file.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                // Play overlay
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
        }
        ext.equals("pdf", ignoreCase = true) -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        else -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = "File", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}
