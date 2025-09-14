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
            ext == "docx" -> openDocxExternally(context, File(fileNode.path)) // ✅ DOCX support
            else -> {
                Toast.makeText(context, "Unsupported file type: $ext", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDocxExternally(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open DOCX", Toast.LENGTH_SHORT).show()
        }
    }
}
