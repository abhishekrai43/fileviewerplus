// File: logic/FileScanner.kt

package com.arapps.fileviewplus.logic

import android.content.Context
import android.os.Environment
import com.arapps.fileviewplus.model.CategoryListType
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson


object FileScanner {

    private val imageExt = listOf("jpg", "jpeg", "png", "gif", "webp")
    private val videoExt = listOf("mp4", "mkv", "avi", "3gp")
    private val docExt = listOf("pdf", "doc", "docx", "txt", "xls", "ppt")

    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())

    private val gson = Gson()
    private const val CACHE_FILE = "cached_file_structure.json"
    private const val PREF_KEY = "last_scan_time"
    private const val SCAN_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

    fun shouldScan(context: Context): Boolean {
        val prefs = context.getSharedPreferences("fileflow", Context.MODE_PRIVATE)
        val lastScan = prefs.getLong(PREF_KEY, 0L)
        return System.currentTimeMillis() - lastScan > SCAN_INTERVAL_MS
    }

    fun loadFromCache(context: Context): List<FileNode.Category> {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        return if (cacheFile.exists()) {
            val json = cacheFile.readText()
            gson.fromJson(json, CategoryListType)
        } else emptyList()
    }

    fun scanAndCache(context: Context): List<FileNode.Category> {
        val result = scanStorage(Environment.getExternalStorageDirectory())

        val prefs = context.getSharedPreferences("fileflow", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_KEY, System.currentTimeMillis()).apply()

        val cacheFile = File(context.filesDir, CACHE_FILE)
        cacheFile.writeText(gson.toJson(result))

        return result
    }

    fun scanStorage(rootDir: File): List<FileNode.Category> {
        val categorized = mutableMapOf<String, MutableMap<String, MutableMap<String, MutableMap<String, MutableList<FileNode>>>>>()
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

            val fileNode = FileNode(
                name = file.name,
                path = file.absolutePath,
                type = FileNode.FileType.fromExtension(ext),
                size = file.length(),
                lastModified = file.lastModified()
            )

            val yearMap = categorized.getOrPut(category) { mutableMapOf() }
            val monthMap = yearMap.getOrPut(year) { mutableMapOf() }
            val dayMap = monthMap.getOrPut(month) { mutableMapOf() }
            val fileList = dayMap.getOrPut(day) { mutableListOf() }

            fileList.add(fileNode)
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
                                    FileNode.Day(
                                        name = dayName,
                                        files = files.sortedByDescending { it.name }
                                    )
                                }.sortedByDescending { it.name }
                            )
                        }.sortedByDescending { monthNameToNumber(it.name) }
                    )
                }.sortedByDescending { it.name.toIntOrNull() ?: 0 }
            )
        }.sortedBy { it.name }
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
