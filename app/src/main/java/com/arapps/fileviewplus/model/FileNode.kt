package com.arapps.fileviewplus.model

import com.google.common.reflect.TypeToken
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
        Image, Video, Audio, Document, Pdf, Text, Apk, Zip,FfpSecure, Other;

        companion object {
            fun fromExtension(ext: String): FileType {
                return when (ext.lowercase()) {
                    "jpg", "jpeg", "png", "webp", "bmp", "gif" -> Image
                    "mp4", "mkv", "3gp", "avi", "mov", "flv" -> Video
                    "mp3", "wav", "aac", "ogg", "flac" -> Audio
                    "pdf" -> Pdf
                    "txt", "log", "md" -> Text
                    "apk" -> Apk
                    "zip", "rar", "7z" -> Zip
                    "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> Document
                    "ffpsecure" -> FfpSecure
                    else -> Other
                }
            }
        }
    }

    companion object {
        fun fromFile(file: File): FileNode = FileNode(
            name = file.name,
            path = file.absolutePath,
            type = FileType.fromExtension(file.extension),
            size = file.length(),
            lastModified = file.lastModified()
        )
    }
}
val CategoryListType = object : TypeToken<List<FileNode.Category>>() {}.type