package com.arapps.fileviewplus.utils

import com.arapps.fileviewplus.model.FileNode

object FileAnalytics {

    data class FileInsight(
        val file: FileNode,
        val size: Long,
        val lastModified: Long
    )

    fun getInsights(files: List<FileNode>): List<FileInsight> {
        return files.map {
            FileInsight(
                file = it,
                size = it.size,
                lastModified = it.lastModified
            )
        }
    }

    fun getOldFiles(insights: List<FileInsight>, olderThanDays: Int): List<FileInsight> {
        val threshold = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        return insights.filter { it.lastModified < threshold }
    }

    fun getLargeFiles(insights: List<FileInsight>, largerThanMB: Int): List<FileInsight> {
        val threshold = largerThanMB * 1024 * 1024L
        return insights.filter { it.size > threshold }
    }
}
