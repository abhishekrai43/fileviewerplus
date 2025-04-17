package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.arapps.fileviewplus.model.FileNode
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {

    /**
     * Create a .zip archive from a list of FileNode objects.
     * @param context Context for file provider
     * @param baseName Desired base name (no extension)
     * @param nodes List of FileNode to include
     * @return Zipped file saved in cacheDir
     */
    fun createZip(context: Context, baseName: String, nodes: List<FileNode>): File? {
        if (nodes.isEmpty()) return null

        val sanitizedBase = baseName.replace("""[^\w\d-_]""".toRegex(), "_")
        val zipFile = File(context.cacheDir, "$sanitizedBase.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                nodes.forEach { node ->
                    val file = File(node.path)
                    if (!file.exists() || !file.canRead()) return@forEach

                    FileInputStream(file).use { input ->
                        val entry = ZipEntry(node.name)
                        zipOut.putNextEntry(entry)
                        input.copyTo(zipOut, 1024)
                        zipOut.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ZipUtils", "Failed to create zip: ${e.message}")
            zipFile.delete()
            return null
        }

        return zipFile
    }

    /**
     * Share a zip file using FileNode
     */
    fun shareZip(context: Context, zipFile: File) {
        if (!zipFile.exists()) {
            Log.w("ZipUtils", "Zip file not found: ${zipFile.absolutePath}")
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Zipped File"))
    }

    /**
     * Zip and share a DocumentFile (e.g., from SAF)
     */
    fun zipDocumentFileAndShare(context: Context, file: DocumentFile) {
        try {
            val tempFile = File.createTempFile("doc_", file.name ?: "file", context.cacheDir)

            context.contentResolver.openInputStream(file.uri)?.use { input ->
                input.copyTo(tempFile.outputStream())
            }

            val tempNode = FileNode(
                name = tempFile.name,
                path = tempFile.absolutePath,
                type = FileNode.FileType.OTHER,
                size = tempFile.length(),
                lastModified = tempFile.lastModified()
            )


            val zip = createZip(context, file.name ?: "archive", listOf(tempNode))
            if (zip != null) {
                shareZip(context, zip)
            } else {
                Toast.makeText(context, "Failed to zip file", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(context, "Zip & Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share a single file using FileNode
     */
    fun shareSingleFile(context: Context, node: FileNode): Boolean {
        return try {
            val file = File(node.path)
            if (!file.exists() || !file.canRead()) return false

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share via"))
            true
        } catch (e: Exception) {
            Log.e("ZipUtils", "Sharing failed: ${e.message}")
            false
        }
    }
}
