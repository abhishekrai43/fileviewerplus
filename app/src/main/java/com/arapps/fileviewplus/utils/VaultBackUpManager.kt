package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object VaultBackupManager {

    fun backupVaultToZip(
        context: Context,
        vaultDir: File,
        onStarted: () -> Unit = {},
        onFailed: (String) -> Unit = {},
        onReadyToShare: (Intent) -> Unit = {}
    ) {
        try {
            onStarted()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(context.cacheDir, "vault_backup_$timestamp.zip")

            ZipOutputStream(backupFile.outputStream()).use { zipOut ->
                zipFolder(vaultDir, vaultDir, zipOut)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.arapps.fileviewplus.fileprovider",
                        backupFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            onReadyToShare(Intent.createChooser(intent, "Upload Vault Backup to Google Drive"))

        } catch (e: Exception) {
            onFailed("Backup failed: ${e.message}")
        }
    }


    private fun zipFolder(baseFolder: File, sourceFile: File, zipOut: ZipOutputStream) {
        if (sourceFile.isDirectory) {
            sourceFile.listFiles()?.forEach { child ->
                zipFolder(baseFolder, child, zipOut)
            }
        } else {
            val relativePath = sourceFile.absolutePath.removePrefix(baseFolder.absolutePath).removePrefix("/")
            val entry = ZipEntry(relativePath)
            zipOut.putNextEntry(entry)
            sourceFile.inputStream().copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
}
