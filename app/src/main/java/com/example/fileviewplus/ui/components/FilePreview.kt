package com.example.fileviewplus.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter   
import java.io.File

@Composable
fun FilePreview(file: File) {
    val context = LocalContext.current
    val uri = Uri.fromFile(file)
    val extension = file.extension.lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

    when {
        mimeType?.startsWith("image") == true -> {
            Image(
                painter = rememberAsyncImagePainter(model = uri),
                contentDescription = "Image Preview",
                modifier = Modifier.size(48.dp)
            )
        }

        mimeType?.startsWith("video") == true -> {
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, uri)
                retriever.frameAtTime?.let { it }
            } catch (_: Exception) { null }
            finally { retriever.release() }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Video Preview",
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(Icons.Default.Movie, contentDescription = "Video", tint = MaterialTheme.colorScheme.primary)
            }
        }

        else -> {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = "File",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
