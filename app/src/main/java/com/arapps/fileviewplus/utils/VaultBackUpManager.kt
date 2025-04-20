package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object VaultBackupManager {

    @RequiresApi(Build.VERSION_CODES.O)
    fun backupVaultToZip(
        context: Context,
        selectedItems: List<File>,
        onStarted: () -> Unit = {},
        onFailed: (String) -> Unit = {},
        onReadyToShare: (Intent) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { onStarted() }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(context.cacheDir, "vault_backup_$timestamp.zip")

                ZipOutputStream(Files.newOutputStream(backupFile.toPath(), StandardOpenOption.CREATE)).use { zipOut ->
                    selectedItems.forEach { file ->
                        zipItem(file.toPath(), file.parentFile?.toPath() ?: file.toPath().parent, zipOut)
                    }
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

                withContext(Dispatchers.Main) {
                    onReadyToShare(Intent.createChooser(intent, "Upload Vault Backup to Google Drive"))
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailed("Backup failed: ${e.message}")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun zipItem(currentPath: Path, basePath: Path, zipOut: ZipOutputStream) {
        if (Files.isDirectory(currentPath)) {
            Files.list(currentPath).use { stream ->
                stream.forEach { childPath ->
                    zipItem(childPath, basePath, zipOut)
                }
            }
        } else {
            val relativePath = basePath.relativize(currentPath).toString().replace("\\", "/")
            zipOut.putNextEntry(ZipEntry(relativePath))
            Files.newInputStream(currentPath).use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()
        }
    }
}
