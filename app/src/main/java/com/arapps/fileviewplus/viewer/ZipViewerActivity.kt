package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import java.io.File
import java.util.zip.ZipFile

class ZipViewerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "path"
        fun launch(context: Context, path: String) {
            val i = Intent(context, ZipViewerActivity::class.java).apply { putExtra(EXTRA_PATH, path) }
            if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No archive to preview", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val entries = try {
            ZipFile(File(path)).use { zip ->
                zip.entries().toList().map { it.name }
            }
        } catch (e: Exception) {
            listOf("Unable to read archive: ${e.localizedMessage}")
        }

        setContent {
            FileFlowPlusTheme {
                ZipPreviewScreen(path, entries)
            }
        }
    }
}

@Composable
fun ZipPreviewScreen(path: String, entries: List<String>) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(text = "Archive: ${path.substringAfterLast('/')}", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries) { e ->
                Text(text = e, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { /* Could add extract/open action later */ }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

