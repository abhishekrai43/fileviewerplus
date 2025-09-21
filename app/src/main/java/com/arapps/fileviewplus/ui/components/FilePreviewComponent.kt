package com.arapps.fileviewplus.ui.components
// NOTE: original package had a typo ('fileflowplus' vs 'fileviewplus') in older versions.
// Kept this package name to avoid breaking references; callers in the project import the
// public function `FilePreviewThumbnail` by fully qualified package where needed. If you
// prefer to move this into `com.arapps.fileviewplus.ui.components`, rename the package and
// update imports across the repo.

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun FilePreviewThumbnail(file: File, modifier: Modifier = Modifier, contentDescription: String? = null) {
    // Use extension if available; otherwise, fall back to name suffix to handle edge cases
    val rawExt = file.extension.ifBlank { file.name.substringAfterLast('.', missingDelimiterValue = "") }
    val ext = rawExt.lowercase(Locale.getDefault())
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
            val ctx = LocalContext.current
            val thumbState = remember(file.path) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(file.path) {
                // Generate thumbnail reliably via MediaMetadataRetriever on IO thread
                val b = withContext(Dispatchers.IO) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            retriever.getFrameAtTime(0)
                        } finally {
                            try { retriever.release() } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                thumbState.value = b
            }

            Box(modifier = modifier) {
                val thumb = thumbState.value
                if (thumb != null) {
                    Image(bitmap = thumb.asImageBitmap(), contentDescription = contentDescription ?: file.name, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                } else {
                    // Fall back to AsyncImage which may still handle frame extraction on some devices
                    AsyncImage(
                        model = file,
                        contentDescription = contentDescription ?: file.name,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
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
        // audio files: show a musical note icon
        ext.matches(Regex("mp3|wav|aac|ogg|flac|m4a|amr|opus|wma", RegexOption.IGNORE_CASE)) -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        // Use primary tint so the musical note stands out over the thumbnail background
                        Icon(imageVector = Icons.Default.MusicNote, contentDescription = "Audio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
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
