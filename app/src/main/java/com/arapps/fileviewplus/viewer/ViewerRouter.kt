package com.arapps.fileviewplus.viewer

import android.content.Context
import android.widget.Toast
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewwplus.viewer.ImageViewerActivity

object ViewerRouter {

    fun openFile(context: Context, fileNode: FileNode, fromVault: Boolean) {
        val ext = fileNode.extension.lowercase()

        try {
            when {
                ext == "pdf" -> PdfViewerActivity.launch(context, fileNode, fromVault)
                ext in listOf("jpg", "jpeg", "png", "webp") -> ImageViewerActivity.launch(context, fileNode, fromVault)
                ext in listOf("txt", "log", "json", "xml", "md") -> TextViewerActivity.launch(context, fileNode, fromVault)
                ext in listOf("mp4", "mkv", "avi", "mov") -> VideoViewerActivity.launch(context, fileNode, fromVault)
                else -> Toast.makeText(context, "Unsupported file format: .$ext", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
