package com.example.fileviewplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fileviewplus.logic.StorageStats

@Composable
fun StorageUsageBar(stats: List<StorageStats.Stat>) {
    val totalBytes = stats.sumOf { it.totalBytes }.coerceAtLeast(1L)

    val categoryColorMap = mapOf(
        "DOC" to Color(0xFF6A1B9A),    // Purple
        "IMG" to Color(0xFF0288D1),    // Blue
        "VID" to Color(0xFF2E7D32)     // Green
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp) // ✅ key change: horizontal padding
    ) {
        stats.forEach { stat ->
            val percent = stat.totalBytes.toFloat() / totalBytes
            val label = "${stat.name} — ${StorageStats.formatSize(stat.totalBytes)} (${(percent * 100).toInt()}%)"
            val barColor = categoryColorMap[stat.name] ?: MaterialTheme.colorScheme.primary

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = barColor
                )
            }
        }
    }
}
