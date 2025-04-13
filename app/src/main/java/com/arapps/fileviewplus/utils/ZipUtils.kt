package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.arapps.fileviewplus.utils.ZipUtils.createZip
import com.arapps.fileviewplus.utils.ZipUtils.shareZip
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {

    /**
     * Create a .zip archive from a list of files.
     * @param context Context for file provider
     * @param baseName Desired base name (no extension)
     * @param files List of files to include
     * @return Zipped file saved in cacheDir
     */
    fun createZip(context: Context, baseName: String, files: List<File>): File? {
        if (files.isEmpty()) return null

        val sanitizedBase = baseName.replace("""[^\w\d-_]""".toRegex(), "_")
        val zipFile = File(context.cacheDir, "$sanitizedBase.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                files.forEach { file ->
                    if (!file.exists() || !file.canRead()) return@forEach

                    FileInputStream(file).use { input ->
                        val entry = ZipEntry(file.name)
                        zipOut.putNextEntry(entry)
                        input.copyTo(zipOut, 1024)
                        zipOut.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ZipUtils", "Failed to create zip: ${e.message}")
            zipFile.delete() // clean up on failure
            return null
        }

        return zipFile
    }

    /**
     * Launch Android share intent for the given ZIP file
     */
    fun shareZip(context: Context, zipFile: File) {
        if (!zipFile.exists()) {
            Log.w("ZipUtils", "Zip file not found: ${zipFile.absolutePath}")
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            zipFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share Zipped File")
        )
    }
    fun zipDocumentFileAndShare(context: Context, file: DocumentFile) {
        try {
            val tempFile = File.createTempFile("doc_", file.name ?: "file", context.cacheDir)

            val input = context.contentResolver.openInputStream(file.uri)
            input?.use { it.copyTo(tempFile.outputStream()) }

            val zip = createZip(context, file.name ?: "archive", listOf(tempFile))
            if (zip != null) {
                shareZip(context, zip)
            } else {
                Toast.makeText(context, "Failed to zip file", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(context, "Zip & Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

}
