package com.arapps.fileflowplus.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FilePreviewThumbnail(file: File) {
    when {
        file.extension.equals("pdf", ignoreCase = true) -> PdfThumbnail(file)
        file.extension.matches(Regex("jpg|jpeg|png|webp|bmp|gif", RegexOption.IGNORE_CASE)) ->
            ImageThumbnail(file)
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
