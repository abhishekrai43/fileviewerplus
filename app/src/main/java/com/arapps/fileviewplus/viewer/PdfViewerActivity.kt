package com.arapps.fileviewplus.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.arapps.fileviewplus.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = resolveFileFromIntent(intent)

        if (file == null || !file.exists() || !file.canRead()) {
            Toast.makeText(this, "Cannot open PDF", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PdfViewerScreen(file)
                }
            }
        }
    }

    private fun resolveFileFromIntent(intent: Intent): File? {
        // Case 1: internal intent with direct file path
        intent.getStringExtra("path")?.let { path ->
            return File(path)
        }

        // Case 2: external open with (content:// or file://)
        val uri = intent.data ?: return null
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(cacheDir, "external_temp_${System.currentTimeMillis()}.pdf")
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra("path", fileNode.path)
                putExtra("fromVault", fromVault)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun PdfViewerScreen(file: File) {
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.path) {
        pages = withContext(Dispatchers.IO) {
            loadPdfPages(file)
        }
    }

    if (pages == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset += pan
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .padding(16.dp)
            ) {
                pages!!.forEach { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

private fun loadPdfPages(file: File): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    try {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descriptor).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pages.add(bitmap)
                page.close()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return pages
}
