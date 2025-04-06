package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.logic.StorageStats

@Composable
fun StorageUsageBar(stats: List<StorageStats.Stat>) {
    // Calculate total bytes across all categories, ensuring it's at least 1 to avoid divide-by-zero
    val totalBytes = stats.sumOf { it.totalBytes }.coerceAtLeast(1L)

    // Define color mapping for each category
    val categoryColorMap = mapOf(
        "DOC" to Color(0xFF6A1B9A),    // Purple
        "IMG" to Color(0xFF0288D1),    // Blue
        "VID" to Color(0xFF2E7D32)     // Green
    )

    // Layout container for the entire storage usage bar
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp) // Added horizontal and vertical padding
    ) {
        // Loop through each stat and display a progress bar
        stats.forEach { stat ->
            // Calculate the percentage for this category
            val percent = (stat.totalBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

            // Label to show the name and percentage for this category
            val label = "${stat.name} — ${StorageStats.formatSize(stat.totalBytes)} (${(percent * 100).toInt()}%)"

            // Get the color associated with this category, default to primary color if not found
            val barColor = categoryColorMap[stat.name] ?: MaterialTheme.colorScheme.primary

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)  // Spacing between bars
            ) {
                // Display the label text
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)  // Padding below the label text
                )

                // Progress bar for this category
                LinearProgressIndicator(
                    progress = percent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),  // Customize progress bar height
                    color = barColor  // Use category-specific color
                )
            }
        }
    }
}
