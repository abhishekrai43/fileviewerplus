package com.arapps.fileviewplus.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.io.FileOutputStream

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = resolveFileFromIntent(intent)

        if (file == null || !file.exists() || !file.canRead()) {
            Toast.makeText(this, "Cannot open image", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = rememberAsyncImagePainter(file),
                        contentDescription = "Image Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
            }
        }
    }

    private fun resolveFileFromIntent(intent: Intent): File? {
        // Case 1: From inside app
        intent.getStringExtra("path")?.let { return File(it) }

        // Case 2: Opened externally
        val uri: Uri = intent.data ?: return null
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val ext = contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
            val tempFile = File(cacheDir, "external_img_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra("path", fileNode.path)
                putExtra("fromVault", fromVault)
            }
            context.startActivity(intent)
        }
    }
}
