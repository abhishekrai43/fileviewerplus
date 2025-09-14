package com.arapps.fileviewplus.repo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.intent.IntentActions
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.model.FileType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.LinkedHashMap

/**
 * FileIndexRepository
 *
 * Single-source-of-truth index for file metadata used by UI tiles and lists.
 *
 * - Persists index to context.filesDir/index_files.json (safe to inspect)
 * - Exposes StateFlows for files and file-counts by day
 * - Listens for ACTION_FILE_DELETED broadcast and removes entries
 * - Call addOrUpdate() to add a discovered file (scanner, startup)
 * - Call remove(path) after successful deletion to update index and fire broadcast
 *
 * Uses FileNode (flat) for storage; navigation uses FileNode.Category/Year/Month/Day trees.
 */
object FileIndexRepository {
    private const val TAG = "FileIndexRepository"
    private const val INDEX_FILENAME = "index_files.json"
    private val gson = Gson()
    private val jsonType: Type = object : TypeToken<List<FileNode>>() {}.type

    // internal mutable state (in-memory canonical index)
    private val _filesState = MutableStateFlow<List<FileNode>>(emptyList())
    val filesFlow: StateFlow<List<FileNode>> = _filesState.asStateFlow()

    // derived counts by day (Map<dayKey -> count>), dayKey format 'yyyy-MM-dd'
    private val _countsByDay = MutableStateFlow<Map<String, Int>>(emptyMap())
    val countsByDayFlow: StateFlow<Map<String, Int>> = _countsByDay.asStateFlow()

    // coroutine scope for background IO operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // date formatter for grouping
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun initialize(context: Context) {
        scope.launch {
            loadPersistedIndex(context)
            registerBroadcastReceiver(context)
            computeCounts()
        }
    }

    suspend fun addOrUpdate(context: Context, node: FileNode) {
        withContext(Dispatchers.IO) {
            val current = _filesState.value.toMutableList()
            val idx = current.indexOfFirst { it.path == node.path }
            if (idx >= 0) {
                current[idx] = node
            } else {
                current.add(node)
            }
            _filesState.value = current.toList()
            persistIndex(context)
            computeCounts()
        }
    }

    suspend fun remove(context: Context, path: String): Boolean {
        return withContext(Dispatchers.IO) {
            val current = _filesState.value.toMutableList()
            val removed = current.removeAll { it.path == path }
            if (removed) {
                _filesState.value = current.toList()
                persistIndex(context)
                computeCounts()
                broadcastDeleted(context, path)
                tryRefreshMediaStore(context, path)
            }
            removed
        }
    }

    fun filesForDate(dayKey: String): Flow<List<FileNode>> {
        return filesFlow.map { list -> list.filter { formatDay(it.lastModified) == dayKey } }
    }

    suspend fun rebuildIndexFromMediaStore(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<FileNode>()
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.DATA,
                    android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.DATE_MODIFIED
                )
                val uris = listOf(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    android.provider.MediaStore.Files.getContentUri("external")
                )

                for (uri in uris) {
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        val dataIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                        val sizeIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
                        val dateIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                        while (cursor.moveToNext()) {
                            try {
                                val name = cursor.getString(nameIdx) ?: continue
                                val data = cursor.getString(dataIdx) ?: continue
                                val size = cursor.getLong(sizeIdx)
                                val date = cursor.getLong(dateIdx) * 1000L
                                val f = File(data)
                                val node = FileNode(
                                    name = name,
                                    path = f.absolutePath,
                                    type = FileNode.FileType.fromExtension(f.extension),
                                    size = size,
                                    lastModified = if (date > 0) date else f.lastModified()
                                )
                                results.add(node)
                            } catch (_: Throwable) { }
                        }
                    }
                }

                val deduped = LinkedHashMap<String, FileNode>()
                for (n in results) deduped.putIfAbsent(n.path, n)
                _filesState.value = deduped.values.toList()
                persistIndex(context)
                computeCounts()
            } catch (t: Throwable) {
                Log.w(TAG, "rebuildIndexFromMediaStore failed: ${t.localizedMessage}")
            }
        }
    }

    private suspend fun loadPersistedIndex(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val f = File(context.filesDir, INDEX_FILENAME)
                if (!f.exists()) {
                    _filesState.value = emptyList()
                    return@withContext
                }
                val raw = f.readText()
                val parsed: List<FileNode> = gson.fromJson(raw, jsonType)
                _filesState.value = parsed
                computeCounts()
            } catch (t: Throwable) {
                Log.w(TAG, "loadPersistedIndex failed: ${t.localizedMessage}")
                _filesState.value = emptyList()
            }
        }
    }

    private fun persistIndex(context: Context) {
        try {
            val f = File(context.filesDir, INDEX_FILENAME)
            val json = gson.toJson(_filesState.value)
            f.writeText(json)
        } catch (t: Throwable) {
            Log.w(TAG, "persistIndex failed: ${t.localizedMessage}")
        }
    }

    private fun computeCounts() {
        try {
            val map = _filesState.value.groupingBy { formatDay(it.lastModified) }.eachCount()
            _countsByDay.value = map
        } catch (t: Throwable) {
            Log.w(TAG, "computeCounts failed: ${t.localizedMessage}")
            _countsByDay.value = emptyMap()
        }
    }

    private fun formatDay(epochMillis: Long): String {
        return dayFormatter.format(Date(epochMillis))
    }

    private fun broadcastDeleted(context: Context, path: String) {
        try {
            val i = Intent(IntentActions.ACTION_FILE_DELETED).apply {
                putExtra(IntentActions.EXTRA_DELETED_PATH, path)
            }
            context.sendBroadcast(i)
        } catch (t: Throwable) {
            Log.w(TAG, "broadcastDeleted failed: ${t.localizedMessage}")
        }
    }

    private fun tryRefreshMediaStore(context: Context, rawPath: String) {
        try {
            val normalized = when {
                rawPath.startsWith("file://") -> Uri.parse(rawPath).path ?: rawPath
                else -> rawPath
            }
            MediaScannerConnection.scanFile(context, arrayOf(normalized), null) { p, uri ->
                Log.d(TAG, "scanFile completed for $p -> $uri")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "tryRefreshMediaStore failed: ${t.localizedMessage}")
        }
    }

    suspend fun pruneMissingFiles(context: Context) {
        withContext(Dispatchers.IO) {
            val current = _filesState.value.toMutableList()
            val changed = current.removeAll { !File(it.path).exists() }
            if (changed) {
                _filesState.value = current.toList()
                persistIndex(context)
                computeCounts()
            }
        }
    }

    private fun registerBroadcastReceiver(context: Context) {
        try {
            val filter = IntentFilter(IntentActions.ACTION_FILE_DELETED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val deleted = intent?.getStringExtra(IntentActions.EXTRA_DELETED_PATH) ?: return
                    scope.launch {
                        try {
                            val updated = _filesState.value.filterNot { it.path == deleted }
                            if (updated.size != _filesState.value.size) {
                                _filesState.value = updated
                                persistIndex(context)
                                computeCounts()
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "broadcastReceiver handling failed: ${t.localizedMessage}")
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            Log.w(TAG, "registerBroadcastReceiver failed: ${t.localizedMessage}")
        }
    }
}
