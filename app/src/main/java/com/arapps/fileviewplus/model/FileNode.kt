package com.arapps.fileviewplus.model

import java.io.File

data class FileNode(
    val name: String,
    val path: String,
    val type: FileType,
    val size: Long,
    val lastModified: Long
) {
    enum class FileType { IMAGE, VIDEO, DOCUMENT }

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
}
