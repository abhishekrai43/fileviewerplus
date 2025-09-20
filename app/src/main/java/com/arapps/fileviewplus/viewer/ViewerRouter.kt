package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.model.FileNode
import java.io.File

object ViewerRouter {

    fun openFile(context: Context, fileNode: FileNode, fromVault: Boolean) {
        val ext = fileNode.extension.lowercase()
        when {
            ext == "pdf" -> PdfViewerActivity.launch(context, fileNode, fromVault)
            ext in listOf("jpg", "jpeg", "png", "webp") -> ImageViewerActivity.launch(context, fileNode, fromVault)
            ext in listOf("txt", "log", "json", "xml", "md") -> TextViewerActivity.launch(context, fileNode, fromVault)
            ext in listOf("mp4", "mkv", "avi", "mov") -> VideoViewerActivity.launch(context, fileNode, fromVault)
            // audio: open with external audio player (in-app Compose player is used elsewhere when available)
            ext in listOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr") -> openExternally(context, File(fileNode.path), getAudioMime(ext))
            ext == "docx" -> openDocxExternally(context, File(fileNode.path)) // ✅ DOCX support
            else -> {
                // Try a best-effort external open with inferred mime type, fallback to chooser or show unsupported
                openExternally(context, File(fileNode.path), inferMime(ext))
            }
        }
    }

    private fun openDocxExternally(context: Context, file: File) {
        openExternally(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }

    private fun openExternally(context: Context, file: File, mime: String?) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAudioMime(ext: String): String {
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "amr" -> "audio/amr"
            else -> "audio/*"
        }
    }

    private fun inferMime(ext: String): String? {
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            else -> null
        }
    }
}
