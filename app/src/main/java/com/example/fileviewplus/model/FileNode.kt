package com.example.fileviewplus.model

import java.io.File

data class FileNode(
    val name: String,
    val path: String,
    val type: FileType
) {
    enum class FileType { IMAGE, VIDEO, DOCUMENT }

    data class Day(val name: String, val files: List<File>)
    data class Month(val name: String, val days: List<Day>)
    data class Category(val name: String, val months: List<Month>)
}
