package com.arapps.fileviewplus.logic

import com.arapps.fileviewplus.model.FileNode
import java.text.DecimalFormat

object StorageStats {

    data class Stat(
        val name: String,
        val totalBytes: Long
    )

    fun calculateStats(categories: List<FileNode.Category>): List<Stat> {
        return categories.map { category ->
            val allFiles = category.months.flatMap { it.days }.flatMap { it.files }
            Stat(category.name, allFiles.sumOf { it.length() })
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
