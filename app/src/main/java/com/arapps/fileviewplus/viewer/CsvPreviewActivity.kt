package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import java.io.File

class CsvPreviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "path"
        fun launch(context: Context, path: String) {
            val i = Intent(context, CsvPreviewActivity::class.java).apply { putExtra(EXTRA_PATH, path) }
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

        val rows = try {
            parseCsvPreview(File(path))
        } catch (e: Exception) {
            listOf(listOf("Unable to parse CSV: ${e.localizedMessage}"))
        }

        setContent {
            FileFlowPlusTheme {
                CsvPreviewScreen(rows)
            }
        }
    }

    private fun parseCsvPreview(file: File, maxLines: Int = 50): List<List<String>> {
        if (!file.exists()) return listOf(listOf("(file not found)"))
        val lines = mutableListOf<List<String>>()
        file.bufferedReader().useLines { seq ->
            seq.take(maxLines).forEach { line ->
                // simple CSV split, not handling quoted commas — lightweight preview only
                val cols = line.split(',').map { it.trim() }
                lines.add(cols)
            }
        }
        if (lines.isEmpty()) return listOf(listOf("(empty)"))
        return lines
    }
}

@Composable
fun CsvPreviewScreen(rows: List<List<String>>) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rows) { idx, row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEachIndexed { ci, c ->
                        val weight = 1f
                        Text(text = c, modifier = Modifier.weight(weight).padding(horizontal = 6.dp), style = if (idx == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

