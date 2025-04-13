package com.arapps.fileviewplus.utils

import java.io.File
import java.security.MessageDigest

object FileAnalytics {

    data class FileInsight(
        val file: File,
        val size: Long,
        val lastModified: Long,

    )

    fun getInsights(files: List<File>): List<FileInsight> {
        return files.map {
            FileInsight(
                file = it,
                size = it.length(),
                lastModified = it.lastModified(),

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
