package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.logic.StorageStats

@Composable
fun StorageUsageBar(stats: List<StorageStats.Stat>) {
    val totalBytes = stats.sumOf { it.totalBytes }.coerceAtLeast(1L)

    val categoryColorMap = mapOf(
        "DOC" to Color(0xFF6A1B9A),
        "IMG" to Color(0xFF0288D1),
        "VID" to Color(0xFF2E7D32)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "Storage Summary",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        stats.forEach { stat ->
            val percent = (stat.totalBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            val label = "${stat.name} — ${StorageStats.formatSize(stat.totalBytes)} (${(percent * 100).toInt()}%)"
            val barColor = categoryColorMap[stat.name] ?: MaterialTheme.colorScheme.primary

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = barColor
                )
            }
        }
    }
}
