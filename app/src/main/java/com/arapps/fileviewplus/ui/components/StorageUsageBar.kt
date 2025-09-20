package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.logic.StorageStats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageUsageBar(stats: List<StorageStats.Stat>) {
    val totalBytes = stats.sumOf { it.totalBytes }.coerceAtLeast(1L)

    // Define a premium color palette for categories
    val categoryColorMap = mapOf(
        "DOC" to Color(0xFF6A1B9A), // purple
        "IMG" to Color(0xFF0288D1), // blue
        "VID" to Color(0xFF2E7D32), // green
        "AUDIO" to Color(0xFFEF6C00), // orange
        "HIDDEN_AUDIO" to Color(0xFFD32F2F) // red
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Storage Summary",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = StorageStats.formatSize(totalBytes),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stacked bar visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                stats.forEach { stat ->
                    val percent = (stat.totalBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                    if (percent > 0f) {
                        val color = categoryColorMap[stat.name] ?: MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(percent)
                                .background(
                                    brush = Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.85f)))
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend: show colored chips with label + size
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            stats.forEach { stat ->
                val percent = (stat.totalBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                val color = categoryColorMap[stat.name] ?: MaterialTheme.colorScheme.primary
                AssistChip(
                    onClick = { /* no-op: purely informational */ },
                    label = {
                        Text(text = "${stat.name} • ${StorageStats.formatSize(stat.totalBytes)} (${(percent * 100).toInt()}%)")
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                )
            }
        }
    }
}
