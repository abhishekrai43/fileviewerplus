package com.arapps.fileviewplus.logic

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import com.arapps.fileviewplus.model.FileNode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileScanner {

    private val imageExt = listOf("jpg", "jpeg", "png", "gif", "webp")
    private val videoExt = listOf("mp4", "mkv", "avi", "3gp", "mov")
    private val docExt = listOf("pdf", "doc", "docx", "txt", "xls", "ppt")
    // new: audio extensions including common mobile recorder formats
    private val audioExt = listOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr")

    @SuppressLint("ConstantLocale")
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    @SuppressLint("ConstantLocale")
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    @SuppressLint("ConstantLocale")
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
            val type = object : TypeToken<List<FileNode.Category>>() {}.type
            gson.fromJson(json, type)
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
        val categorized =
            mutableMapOf<String, MutableMap<String, MutableMap<String, MutableMap<String, MutableList<FileNode>>>>>()


        rootDir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val ext = file.extension.lowercase(Locale.getDefault())

            // Detect hidden recorder files specifically (.amr/.m4a often used by recorder apps)
            val looksLikeHiddenRecorder = (ext in listOf("amr", "m4a")) && (
                file.name.startsWith('.') ||
                    file.parentFile?.name?.startsWith('.') == true ||
                    file.name.lowercase(Locale.getDefault()).contains("record") ||
                    file.name.lowercase(Locale.getDefault()).contains("rec")
                )

            val category = when {
                imageExt.contains(ext) -> "IMG"
                videoExt.contains(ext) -> "VID"
                docExt.contains(ext) -> "DOC"
                looksLikeHiddenRecorder -> "HIDDEN_AUDIO"
                audioExt.contains(ext) -> "AUDIO"
                // previously unknown extensions were ignored; include them under "OTH" so all files are discovered
                else -> "OTH"
            }

            val lastMod = Date(file.lastModified())
            val year = yearFormat.format(lastMod)
            val month = monthFormat.format(lastMod)
            val day = dayFormat.format(lastMod)

            val fileNode = FileNode.fromFile(file)

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
