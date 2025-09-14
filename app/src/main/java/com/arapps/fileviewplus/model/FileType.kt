package com.arapps.fileviewplus.model

/**
 * FileType - categorizes a file into a broad type based on its extension.
 * Extend this enum with more types/extensions as needed.
 */
enum class FileType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    OTHER;

    companion object {
        fun fromExtension(ext: String): FileType {
            val e = ext.lowercase()
            return when (e) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic" -> IMAGE
                "mp4", "mkv", "webm", "avi", "mov" -> VIDEO
                "mp3", "wav", "m4a", "aac", "flac" -> AUDIO
                "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "rtf" -> DOCUMENT
                "zip", "rar", "7z", "tar", "gz" -> ARCHIVE
                else -> OTHER
            }
        }
    }
}
