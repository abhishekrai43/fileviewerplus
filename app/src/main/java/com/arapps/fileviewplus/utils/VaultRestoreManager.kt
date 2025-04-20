package com.arapps.fileviewplus.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipInputStream

object VaultRestoreManager {

    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Failure(val message: String) : RestoreResult()
    }

    suspend fun restoreVaultFromZip(
        context: Context,
        zipUri: Uri,
        vaultDir: File
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry

                    while (entry != null) {
                        val rawName = entry.name
                        if (rawName.isNullOrBlank()) {
                            return@withContext RestoreResult.Failure("Backup file contains a blank entry")
                        }

                        val cleanFile = File(vaultDir, rawName).canonicalFile
                        if (!cleanFile.path.startsWith(vaultDir.canonicalPath)) {
                            Log.w("VaultRestore", "Rejected suspicious entry: $rawName")
                            return@withContext RestoreResult.Failure("Backup contains invalid file paths")
                        }

                        if (entry.isDirectory) {
                            if (!cleanFile.exists() && !cleanFile.mkdirs()) {
                                return@withContext RestoreResult.Failure("Could not create folder: ${cleanFile.name}")
                            }
                        } else {
                            try {
                                cleanFile.parentFile?.mkdirs()
                                cleanFile.outputStream().use { output ->
                                    zipIn.copyTo(output)
                                }
                            } catch (e: IOException) {
                                Log.e("VaultRestore", "Write failed for ${cleanFile.name}", e)
                                return@withContext RestoreResult.Failure("Could not write file: ${cleanFile.name}")
                            }
                        }

                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                return@withContext RestoreResult.Success
            }

            return@withContext RestoreResult.Failure("Could not open backup file")
        } catch (e: FileNotFoundException) {
            Log.e("VaultRestore", "Drive file access error", e)
            return@withContext RestoreResult.Failure(
                "This file could not be accessed. If it's on Google Drive, please download it to your device before restoring."
            )
        } catch (e: SecurityException) {
            Log.e("VaultRestore", "Permission denied", e)
            return@withContext RestoreResult.Failure("Permission denied to access this file.")
        } catch (e: Exception) {
            Log.e("VaultRestore", "Unexpected crash during restore", e)
            return@withContext RestoreResult.Failure("Something went wrong during restore. Please try a different file.")
        }
    }
}
