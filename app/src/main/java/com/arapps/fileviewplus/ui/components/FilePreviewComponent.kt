package com.arapps.fileflowplus.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun FilePreviewThumbnail(file: File) {
    when {
        file.extension.equals("pdf", ignoreCase = true) -> PdfThumbnail(file)
        file.extension.matches(Regex("jpg|jpeg|png|webp|bmp|gif", RegexOption.IGNORE_CASE)) ->
            ImageThumbnail(file)
        file.extension.matches(Regex("mp4|mkv|webm|avi|mov", RegexOption.IGNORE_CASE)) -> VideoThumbnail(file) // ✅ Add this
    }
}

@Composable
private fun PdfThumbnail(file: File) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            val page = renderer.openPage(0)
            val bmp = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            bitmap = bmp
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "PDF thumbnail",
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun ImageThumbnail(file: File) {
    val bitmap = remember(file) {
        BitmapFactory.decodeFile(file.absolutePath)
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Image thumbnail",
            modifier = Modifier.size(48.dp)
        )
    }
}
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun VideoThumbnail(file: File) {
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        runCatching {
            val frame = android.media.ThumbnailUtils.createVideoThumbnail(
                file,
                android.util.Size(240, 240), // adjust size as needed
                null // no cancellation signal
            )
            thumbnail = frame
        }
    }

    thumbnail?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Video thumbnail",
            modifier = Modifier.size(48.dp)
        )
    }
}

