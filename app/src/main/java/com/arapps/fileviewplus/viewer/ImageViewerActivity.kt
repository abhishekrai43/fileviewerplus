package com.arapps.fileviewwplus.viewer

import android.os.Bundle
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

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("path")
        if (path.isNullOrBlank()) {
            finish()
            return
        }

        val file = File(path)
        if (!file.exists() || !file.canRead()) {
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

    companion object {
        fun launch(context: android.content.Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = android.content.Intent(context, ImageViewerActivity::class.java).apply {
                putExtra("path", fileNode.path)
                putExtra("fromVault", fromVault)
            }
            context.startActivity(intent)
        }
    }
}
