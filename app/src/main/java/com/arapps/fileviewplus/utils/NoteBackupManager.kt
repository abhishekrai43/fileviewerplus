// File: com/arapps/fileviewplus/utils/NoteBackupManager.kt

package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NoteBackupManager {

    fun backupNotes(context: Context): File? {
        val notesDir = File(context.filesDir, "notes")
        if (!notesDir.exists() || notesDir.listFiles().isNullOrEmpty()) return null

        val zipFile = File(context.cacheDir, "vault-notes-backup.zip")
        return try {
            ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                notesDir.listFiles()?.forEach { file ->
                    zipOut.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
            zipFile
        } catch (e: Exception) {
            null
        }
    }

    fun shareBackup(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Vault Notes Backup"))
    }
}
