package com.arapps.fileviewplus.model

import java.io.File

data class FileNode(
    val name: String,
    val path: String,
    val type: FileType,
    val size: Long,
    val lastModified: Long
) {
    val extension: String
        get() = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    data class Day(
        val name: String,
        val files: List<FileNode>
    )

    data class Month(
        val name: String,
        val days: List<Day>
    )

    data class Year(
        val name: String,
        val months: List<Month>
    )

    data class Category(
        val name: String,
        val years: List<Year>
    )

    enum class FileType {
        IMAGE,
        VIDEO,
        DOCUMENT,
        OTHER;

        companion object {
            fun from(file: File): FileType {
                val ext = file.extension.lowercase()
                return when (ext) {
                    "jpg", "jpeg", "png", "webp", "gif", "bmp" -> IMAGE
                    "mp4", "mkv", "avi", "mov", "3gp" -> VIDEO
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml" -> DOCUMENT
                    else -> OTHER
                }
            }
        }
    }

    companion object {
        fun fromFile(file: File): FileNode = FileNode(
            name = file.name,
            path = file.absolutePath,
            type = FileType.from(file),
            size = file.length(),
            lastModified = file.lastModified()
        )
    }
}
