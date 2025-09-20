package com.arapps.fileviewplus.logic


import com.arapps.fileviewplus.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import android.content.Context
import android.provider.MediaStore
import android.database.Cursor

object StorageStats {

    data class Stat(
        val name: String,
        val totalBytes: Long,
        val count: Int = 0
    )

    /**
     * Query MediaStore to compute size totals for common media types.
     * This is more reliable for reporting storage usage of media than a filesystem walk,
     * because MediaStore indexes media accessible under scoped storage.
     */
    suspend fun calculateMediaStoreStats(context: Context): List<Stat> = withContext(Dispatchers.IO) {
        suspend fun querySum(uri: android.net.Uri, projectionColumn: String, selection: String? = null, selectionArgs: Array<String>? = null): Long {
            var sum = 0L
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, arrayOf(projectionColumn), selection, selectionArgs, null)
                if (cursor != null) {
                    val idx = cursor.getColumnIndex(projectionColumn)
                    while (cursor.moveToNext()) {
                        try {
                            sum += cursor.getLong(idx)
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: SecurityException) {
                // Permission not granted - return 0
                return@querySum 0L
            } catch (_: Exception) {
                return@querySum 0L
            } finally {
                cursor?.close()
            }
            return@querySum sum
        }

        suspend fun queryCount(uri: android.net.Uri, selection: String? = null, selectionArgs: Array<String>? = null): Int {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)
                if (cursor != null) return cursor.count
            } catch (_: SecurityException) {
                return 0
            } catch (_: Exception) {
                return 0
            } finally {
                cursor?.close()
            }
            return 0
        }

        val imgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val vidUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val audUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // For documents, use MediaStore.Files and filter common document mime types/extensions
        val filesUri = MediaStore.Files.getContentUri("external")

        val imgBytes = querySum(imgUri, MediaStore.Images.Media.SIZE)
        val vidBytes = querySum(vidUri, MediaStore.Video.Media.SIZE)
        val audBytes = querySum(audUri, MediaStore.Audio.Media.SIZE)

        val imgCount = queryCount(imgUri)
        val vidCount = queryCount(vidUri)
        val audCount = queryCount(audUri)

        // Documents: look for common document extensions (pdf, doc, docx, xls, xlsx, ppt, pptx, txt)
        val docSelection = "(${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? OR ${MediaStore.Files.FileColumns.MIME_TYPE}=? )"
        // Common MIME types - best-effort
        val docMimeTypes = arrayOf("application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","text/plain")
        val docBytes = querySum(filesUri, MediaStore.Files.FileColumns.SIZE, docSelection, docMimeTypes)
        val docCount = queryCount(filesUri, docSelection, docMimeTypes)

        listOf(
            Stat("DOC", docBytes, docCount),
            Stat("IMG", imgBytes, imgCount),
            Stat("VID", vidBytes, vidCount),
            Stat("AUDIO", audBytes, audCount)
        )
    }

    /**
     * Calculate total bytes per category.
     *
     * Runs on Dispatchers.IO to avoid blocking the main thread for large indexes.
     */
    suspend fun calculateStats(categories: List<FileNode.Category>): List<Stat> = withContext(Dispatchers.IO) {
        categories.map { category ->
            // traverse hierarchy safely and efficiently
            val allFiles = category.years
                .asSequence()
                .flatMap { it.months.asSequence() }
                .flatMap { it.days.asSequence() }
                .flatMap { it.files.asSequence() }

            val totalBytes = allFiles.mapNotNull { it.size.takeIf { s -> s >= 0L } }.sum()
            val fileCount = category.years
                .asSequence()
                .flatMap { it.months.asSequence() }
                .flatMap { it.days.asSequence() }
                .flatMap { it.files.asSequence() }
                .count()

            Stat(name = category.name, totalBytes = totalBytes, count = fileCount)
        }
    }

    /**
     * Nicely format bytes into KB/MB/GB with two-decimal precision.
     * - Uses powers of 1024.
     * - Always returns human-readable string.
     */
    fun formatSize(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        val df = DecimalFormat("#.##")

        return when {
            bytes >= gb -> "${df.format(bytes.toDouble() / gb)} GB"
            bytes >= mb -> "${df.format(bytes.toDouble() / mb)} MB"
            bytes >= kb -> "${df.format(bytes.toDouble() / kb)} KB"
            else -> "$bytes B"
        }
    }
}
