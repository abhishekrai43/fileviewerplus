package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun createZip(context: Context, baseName: String, files: List<File>): File {
        val zipFile = File(context.cacheDir, "$baseName.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            files.forEach { file ->
                if (!file.exists() || !file.canRead()) return@forEach

                val input = FileInputStream(file)
                val entry = ZipEntry(file.name)
                zipOut.putNextEntry(entry)
                input.copyTo(zipOut, 1024)
                input.close()
            }
        }
        return zipFile
    }

    fun shareZip(context: Context, zipFile: File) {
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
        context.startActivity(Intent.createChooser(intent, "Share Zip File"))
    }
}
