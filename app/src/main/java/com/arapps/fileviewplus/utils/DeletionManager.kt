package com.arapps.fileviewplus.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.arapps.fileviewplus.intent.IntentActions
import com.arapps.fileviewplus.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Production-grade DeletionManager
 *
 * Responsibilities:
 *  - Delete a file represented by FileNode (content:// or filesystem path)
 *  - Return a DeleteResult describing outcome:
 *      * Deleted -> success
 *      * NeedUserGrant -> caller must request SAF permission (may include suggestedUriToOpen)
 *      * Failed -> deletion failed with a reason
 *
 *  - When deletion succeeds, notifies MediaStore (scanning / removal) and broadcasts ACTION_FILE_DELETED
 *
 * Notes:
 *  - This module is intentionally conservative: it tries simple deletes first and only asks for user grant
 *    when Android APIs or filesystem permissions prevent direct deletion.
 *  - UI should call this off the UI thread (we follow that here using withContext for any blocking IO).
 */
object DeletionManager {

    sealed class DeleteResult {
        object Deleted : DeleteResult()
        data class NeedUserGrant(val suggestedUriToOpen: Uri?, val message: String) : DeleteResult()
        data class Failed(val reason: String) : DeleteResult()
    }

    private const val TAG = "DeletionManager"

    /**
     * Delete a file represented by FileNode.
     *
     * This is safe to call from a coroutine. It runs I/O on Dispatchers.IO internally.
     */
    suspend fun deleteFile(context: Context, node: FileNode): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val rawPath = node.path.trim()
            // content URI
            if (rawPath.startsWith("content://", ignoreCase = true)) {
                return@withContext deleteContentUri(context, Uri.parse(rawPath))
            }

            // file URI (file://) -> normalize
            val normalizedPath = if (rawPath.startsWith("file://", ignoreCase = true)) {
                Uri.parse(rawPath).path ?: rawPath.removePrefix("file://")
            } else rawPath

            // Normal filesystem path
            val file = File(normalizedPath)
            if (!file.exists()) {
                // Already gone — treat as success (but still notify app)
                broadcastDeleted(context, normalizedPath)
                refreshMediaStore(context, normalizedPath)
                return@withContext DeleteResult.Deleted
            }

            // Try simple delete
            val deleted = try {
                file.delete()
            } catch (t: Throwable) {
                Log.w(TAG, "File.delete() threw: ${t.localizedMessage}")
                false
            }

            if (deleted) {
                broadcastDeleted(context, normalizedPath)
                refreshMediaStore(context, normalizedPath)
                return@withContext DeleteResult.Deleted
            }

            // On Android 29+ we can try MediaStore deletion if the file is in external storage (scoped storage)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val msRes = tryDeleteViaMediaStore(context, file)
                if (msRes) {
                    broadcastDeleted(context, normalizedPath)
                    refreshMediaStore(context, normalizedPath)
                    return@withContext DeleteResult.Deleted
                }
            }

            // At this point, delete failed probably due to permission restrictions. Ask user to grant access via SAF.
            // We cannot reliably construct the exact tree Uri for every path across devices; return NeedUserGrant
            // so caller can open a folder picker. We include the parent folder as a hint if available.
            val suggested: Uri? = try {
                val parent = file.parentFile
                parent?.let { uriForFolderHint(it) }
            } catch (t: Throwable) {
                null
            }

            return@withContext DeleteResult.NeedUserGrant(
                suggested,
                "App needs permission to delete this file. Please select the containing folder to allow deletion."
            )
        } catch (t: Throwable) {
            Log.w(TAG, "deleteFile failed: ${t.localizedMessage}")
            return@withContext DeleteResult.Failed("Unexpected error: ${t.localizedMessage ?: "unknown"}")
        }
    }

    // -- helpers --

    private fun broadcastDeleted(context: Context, path: String) {
        try {
            val intent = Intent(IntentActions.ACTION_FILE_DELETED).apply {
                putExtra(IntentActions.EXTRA_DELETED_PATH, path)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "broadcastDeleted failed: ${t.localizedMessage}")
        }
    }

    private fun refreshMediaStore(context: Context, rawPath: String) {
        try {
            val normalized = normalizePath(rawPath)
            MediaScannerConnection.scanFile(context, arrayOf(normalized), null) { p, uri ->
                Log.d(TAG, "scanFile completed for $p -> $uri")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "refreshMediaStore failed: ${t.localizedMessage}")
        }
    }

    private fun normalizePath(p: String): String {
        return when {
            p.startsWith("file://") -> Uri.parse(p).path ?: p
            else -> p
        }
    }

    private fun deleteContentUri(context: Context, uri: Uri): DeleteResult {
        return try {
            val cr = context.contentResolver
            val rows = cr.delete(uri, null, null)
            if (rows >= 0) {
                // Some providers return number of rows; treat >=0 as success
                broadcastDeleted(context, uri.toString())
                refreshMediaStore(context, uri.toString())
                DeleteResult.Deleted
            } else {
                // Could be a permission issue
                Log.w(TAG, "contentResolver.delete returned $rows for $uri")
                DeleteResult.NeedUserGrant(null, "Need permission to delete the file.")
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "delete content uri permission error: ${security.localizedMessage}")
            DeleteResult.NeedUserGrant(null, "Need permission to delete the file.")
        } catch (t: Throwable) {
            Log.w(TAG, "delete content uri failed: ${t.localizedMessage}")
            DeleteResult.Failed("Failed to delete content uri: ${t.localizedMessage}")
        }
    }

    /**
     * Try to delete a file via MediaStore on Android Q+.
     * This is useful when the app lacks direct filesystem permissions due to scoped storage.
     */
    private fun tryDeleteViaMediaStore(context: Context, file: File): Boolean {
        return try {
            val cr = context.contentResolver
            // Lookup by absolute path in MediaStore. This works for common media files (images, video, audio).
            val selection = "${MediaStore.MediaColumns.DATA}=?"
            val selectionArgs = arrayOf(file.absolutePath)
            val uri = when {
                isImage(file) -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                isVideo(file) -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                isAudio(file) -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }

            cr.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val deleteUri = ContentUris.withAppendedId(uri, id)
                    try {
                        val deleted = cr.delete(deleteUri, null, null)
                        Log.d(TAG, "MediaStore delete for ${file.absolutePath} returned $deleted")
                        return deleted >= 0
                    } catch (se: SecurityException) {
                        Log.w(TAG, "MediaStore delete security exception: ${se.localizedMessage}")
                        return false
                    } catch (t: Throwable) {
                        Log.w(TAG, "MediaStore delete failed: ${t.localizedMessage}")
                        return false
                    }
                } else {
                    Log.d(TAG, "MediaStore: no entry for ${file.absolutePath}")
                    return false
                }
            } ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "tryDeleteViaMediaStore failed: ${t.localizedMessage}")
            false
        }
    }

    private fun isImage(f: File) = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").any { f.name.endsWith(".$it", ignoreCase = true) }
    private fun isVideo(f: File) = listOf("mp4", "mkv", "webm", "mov", "avi").any { f.name.endsWith(".$it", ignoreCase = true) }
    private fun isAudio(f: File) = listOf("mp3", "wav", "m4a", "flac").any { f.name.endsWith(".$it", ignoreCase = true) }

    /**
     * Provide a folder hint Uri for SAF pickers. We cannot guarantee a device will accept it,
     * but it improves usability by opening the picker at the containing folder when possible.
     *
     * This creates a vendor-neutral hint using the "primary" tree format for external storage:
     * content://com.android.externalstorage.documents/tree/primary:PATH
     *
     * If the file is on an SD card or other volume this will likely be incorrect; caller should
     * still be prepared for a null or for the user to manually pick the folder.
     */
    private fun uriForFolderHint(folder: File): Uri? {
        return try {
            val absolute = folder.absolutePath // e.g. /storage/emulated/0/DCIM/Camera
            val segments = absolute.split("/").filter { it.isNotBlank() }
            // ensure at least "storage", "emulated", "0", ...
            val storageIndex = segments.indexOfFirst { it.equals("storage", ignoreCase = true) }
            val relative = if (storageIndex >= 0 && storageIndex + 2 < segments.size) {
                // Build path after the emulated/0 segment
                val rel = segments.subList(storageIndex + 2, segments.size).joinToString(separator = "/")
                "primary:$rel"
            } else {
                // Fallback: give the top-level "primary:" hint
                "primary:"
            }
            // Build tree Uri
            Uri.parse("content://com.android.externalstorage.documents/tree/$relative")
        } catch (t: Throwable) {
            Log.w(TAG, "uriForFolderHint failed: ${t.localizedMessage}")
            null
        }
    }
}
