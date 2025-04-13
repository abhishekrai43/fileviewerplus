package com.arapps.fileviewplus.logic


import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileScanner {

    private val imageExt = listOf("jpg", "jpeg", "png", "gif", "webp")
    private val videoExt = listOf("mp4", "mkv", "avi", "3gp")
    private val docExt = listOf("pdf", "doc", "docx", "txt", "xls", "ppt")

    private val monthFormat = SimpleDateFormat("MMM-yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())

    fun scanStorage(rootDir: File): List<FileNode.Category> {
        val categorized = mutableMapOf<String, MutableMap<String, MutableMap<String, MutableMap<String, MutableList<File>>>>>()

        val dayFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault()) // Just "Jan", "Feb"
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

        rootDir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach

            val ext = file.extension.lowercase(Locale.getDefault())
            val category = when {
                imageExt.contains(ext) -> "IMG"
                videoExt.contains(ext) -> "VID"
                docExt.contains(ext) -> "DOC"
                else -> return@forEach
            }

            val lastMod = Date(file.lastModified())
            val year = yearFormat.format(lastMod)
            val month = monthFormat.format(lastMod)
            val day = dayFormat.format(lastMod)

            val yearMap = categorized.getOrPut(category) { mutableMapOf() }
            val monthMap = yearMap.getOrPut(year) { mutableMapOf() }
            val dayMap = monthMap.getOrPut(month) { mutableMapOf() }
            val fileList = dayMap.getOrPut(day) { mutableListOf() }

            fileList.add(file)
        }

        return categorized.map { (categoryName, yearMap) ->
            FileNode.Category(
                name = categoryName,
                years = yearMap.map { (yearName, monthMap) ->
                    FileNode.Year(
                        name = yearName,
                        months = monthMap.map { (monthName, dayMap) ->
                            FileNode.Month(
                                name = monthName,
                                days = dayMap.map { (dayName, files) ->
                                    FileNode.Day(name = dayName, files = files)
                                }.sortedByDescending { it.name }
                            )
                        }.sortedByDescending { monthNameToNumber(it.name) }
                    )
                }.sortedByDescending { it.name.toIntOrNull() ?: 0 }
            )
        }.sortedBy { it.name } // DOC, IMG, VID
    }
    private fun monthNameToNumber(month: String): Int {
        return try {
            SimpleDateFormat("MMM", Locale.getDefault()).parse(month)?.let {
                Calendar.getInstance().apply { time = it }.get(Calendar.MONTH)
            } ?: -1
        } catch (e: Exception) {
            -1
        }
    }

}
