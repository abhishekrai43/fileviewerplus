package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import java.io.File
import java.util.zip.ZipFile

class DocxPreviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "path"
        fun launch(context: Context, path: String) {
            val i = Intent(context, DocxPreviewActivity::class.java).apply { putExtra(EXTRA_PATH, path) }
            if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No file to preview", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val previewText = try {
            extractDocxText(File(path))
        } catch (e: Exception) {
            e.printStackTrace()
            "Unable to preview file: ${e.localizedMessage}"
        }

        setContent {
            val isDark = false
            FileFlowPlusTheme(darkTheme = isDark) {
                DocxPreviewScreen(previewText)
            }
        }
    }

    private fun extractDocxText(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return "(No document.xml found)"
            val stream = zip.getInputStream(entry)
            val xml = stream.reader(charset = Charsets.UTF_8).use { it.readText() }
            // Very lightweight extraction: remove tags and common xml entities
            var text = xml.replace(Regex("<[^>]+>"), " ")
            text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"'.toString()).replace("&apos;", "'")
            // Collapse whitespace
            text = text.replace(Regex("\\s+"), " ").trim()
            return text.takeIf { it.isNotBlank() } ?: "(Empty document)"
        }
    }
}

@Composable
fun DocxPreviewScreen(text: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
