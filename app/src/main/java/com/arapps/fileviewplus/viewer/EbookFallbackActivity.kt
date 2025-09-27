package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.nio.charset.Charset

private const val EXTRA_PATH = "extra_path"

class EbookFallbackActivity : ComponentActivity() {
    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, EbookFallbackActivity::class.java).apply { putExtra(EXTRA_PATH, fileNode.path); putExtra("fromVault", fromVault) }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No file found", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        val file = File(path)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EbookFallbackScreen(file = file)
                }
            }
        }
    }
}

@Composable
fun EbookFallbackScreen(file: File) {
    var contentPreview by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file.absolutePath) {
        try {
            // Try to read some text safely (compatible with older API levels); if decoding fails we'll show a hex preview
            val bytes = try {
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    val read = input.read(buffer)
                    if (read > 0) buffer.copyOf(read) else ByteArray(0)
                }
            } catch (_: Exception) { ByteArray(0) }
            val text = try { if (bytes.isNotEmpty()) String(bytes, Charset.forName("UTF-8")) else null } catch (_: Exception) { null }
            contentPreview = text ?: bytes.joinToString(separator = " ") { String.format("%02X", it) }
        } catch (e: Exception) {
            contentPreview = "Unable to preview"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = file.name, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Size: ${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))

        contentPreview?.let { preview ->
            Surface(modifier = Modifier
                .weight(1f)
                .fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Column(modifier = Modifier.padding(12.dp).verticalScroll(remember { ScrollState(0) })) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // Try open externally
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx,
                        ctx.packageName + ".fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, null); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(Intent.createChooser(intent, "Open with"))
                } catch (e: Exception) {
                    Toast.makeText(ctx, "No external app found", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Open externally") }
        }
    }
}
