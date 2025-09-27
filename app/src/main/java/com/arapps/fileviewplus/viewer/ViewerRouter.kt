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

    // Lists of extensions mapped to internal viewers or to generic handling.
    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    private val textExts = setOf("txt", "log", "json", "xml", "md", "csv", "html", "htm", "srt", "vtt")
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
    private val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr", "opus", "wma")
    private val pdfExts = setOf("pdf")
    private val officeDocx = setOf("docx", "doc")
    private val officeSheets = setOf("xlsx", "xls", "csv")
    private val officeSlides = setOf("pptx", "ppt")
    private val archiveExts = setOf("zip", "tar", "gz", "7z", "rar")
    // E-book extensions – no in-app viewer, open externally where a reader app exists
    private val ebookExts = setOf("epub", "mobi", "azw", "azw3", "kf8", "fb2", "pdb", "lit", "prc")

    fun openFile(context: Context, fileNode: FileNode, fromVault: Boolean) {
        val ext = fileNode.extension.lowercase()
        when {
            ext in pdfExts -> PdfViewerActivity.launch(context, fileNode, fromVault)
            ext in imageExts -> ImageViewerActivity.launch(context, fileNode, fromVault)
            ext in textExts -> TextViewerActivity.launch(context, fileNode, fromVault)
            ext in videoExts -> VideoViewerActivity.launch(context, fileNode, fromVault)
            ext in audioExts -> {
                // audio: prefer inline playback via PlaybackController, fallback to an activity
                try {
                    PlaybackController.play(fileNode)
                } catch (_: Exception) {
                    try { AudioViewerActivity.launch(context, fileNode, fromVault) } catch (_: Exception) {}
                }
            }
            // In-app ebook support where feasible
            ext == "epub" -> {
                try {
                    EpubViewerActivity.launch(context, fileNode, fromVault)
                } catch (_: Exception) {
                    // fallback to external open
                    openExternally(context, File(fileNode.path), inferMime(ext) ?: "application/octet-stream")
                }
            }
            ext == "fb2" -> {
                try {
                    Fb2ViewerActivity.launch(context, fileNode, fromVault)
                } catch (_: Exception) {
                    openExternally(context, File(fileNode.path), inferMime(ext) ?: "application/octet-stream")
                }
            }
            ext in ebookExts -> {
                // Other ebook formats: try an in-app fallback viewer that provides preview + external open option
                try {
                    EbookFallbackActivity.launch(context, fileNode, fromVault)
                } catch (_: Exception) {
                    openExternally(context, File(fileNode.path), inferMime(ext) ?: "application/octet-stream")
                }
            }
            ext in officeDocx -> {
                // prefer lightweight in-app preview when available
                try {
                    DocxPreviewActivity.launch(context, fileNode.path)
                } catch (_: Exception) {
                    openExternally(context, File(fileNode.path), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                }
            }
            ext in officeSheets -> {
                // Sheets: show quick preview for CSV/XLSX if available, else external
                if (ext == "csv") {
                    try { CsvPreviewActivity.launch(context, fileNode.path) } catch (_: Exception) { TextViewerActivity.launch(context, fileNode, fromVault) }
                } else {
                    openExternally(context, File(fileNode.path), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                }
            }
            ext in officeSlides -> {
                openExternally(context, File(fileNode.path), "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            }
            ext in archiveExts -> {
                // Archives: prefer lightweight internal listing preview, fallback to external
                try {
                    ZipViewerActivity.launch(context, fileNode.path)
                } catch (_: Exception) {
                    openExternally(context, File(fileNode.path), "application/zip")
                }
            }
            else -> {
                // Best-effort external open with inferred mime type
                openExternally(context, File(fileNode.path), inferMime(ext))
            }
        }
    }

    // Public helper to allow callers to ask if extension has an internal viewer available
    fun hasInternalViewerForExtension(extRaw: String): Boolean {
        val ext = extRaw.lowercase()
        return ext in pdfExts || ext in imageExts || ext in textExts || ext in videoExts || ext in audioExts || ext in archiveExts || ext in ebookExts
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

    private fun inferMime(ext: String): String? {
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "zip" -> "application/zip"
            "csv" -> "text/csv"
            // E-book MIME types (best-effort)
            "epub" -> "application/epub+zip"
            "mobi" -> "application/x-mobipocket-ebook"
            "azw", "azw3", "kf8" -> "application/vnd.amazon.ebook"
            "fb2" -> "application/xml"
            "pdb", "prc", "lit" -> "application/octet-stream"
            else -> null
        }
    }
}
