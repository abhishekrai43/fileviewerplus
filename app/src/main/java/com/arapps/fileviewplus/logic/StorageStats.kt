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

    suspend fun calculateStats(categories: List<FileNode.Category>): List<Stat> =
        withContext(Dispatchers.IO) {
            categories.map { category ->
                val allFiles = category.years
                    .flatMap { it.months }
                    .flatMap { it.days }
                    .flatMap { it.files }

                val totalBytes = allFiles.sumOf { it.size }
                Stat(category.name, totalBytes)
            }
        }

    fun formatSize(bytes: Long): String {
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024
        val df = DecimalFormat("#.##")

        return when {
            bytes >= gb -> "${df.format(bytes.toFloat() / gb)} GB"
            bytes >= mb -> "${df.format(bytes.toFloat() / mb)} MB"
            bytes >= kb -> "${df.format(bytes.toFloat() / kb)} KB"
            else -> "$bytes B"
        }
    }
}
