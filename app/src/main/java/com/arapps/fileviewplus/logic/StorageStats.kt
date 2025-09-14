package com.arapps.fileviewplus.logic


import com.arapps.fileviewplus.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

object StorageStats {

    data class Stat(
        val name: String,
        val totalBytes: Long
    )

    /**
     * Calculate total bytes per category.
     *
     * Runs on Dispatchers.IO to avoid blocking the main thread for large indexes.
     */
    suspend fun calculateStats(categories: List<FileNode.Category>): List<Stat> = withContext(Dispatchers.IO) {
        categories.map { category ->
            // traverse hierarchy safely and efficiently
            val totalBytes = category.years
                .asSequence()
                .flatMap { it.months.asSequence() }
                .flatMap { it.days.asSequence() }
                .flatMap { it.files.asSequence() }
                .mapNotNull { it.size.takeIf { s -> s >= 0L } } // defensive
                .sum()

            Stat(name = category.name, totalBytes = totalBytes)
        }
    }

    /**
     * Nicely format bytes into KB/MB/GB with two-decimal precision.
     * - Uses powers of 1024.
     * - Always returns human-readable string.
     */
    fun formatSize(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        val df = DecimalFormat("#.##")

        return when {
            bytes >= gb -> "${df.format(bytes.toDouble() / gb)} GB"
            bytes >= mb -> "${df.format(bytes.toDouble() / mb)} MB"
            bytes >= kb -> "${df.format(bytes.toDouble() / kb)} KB"
            else -> "$bytes B"
        }
    }
}
