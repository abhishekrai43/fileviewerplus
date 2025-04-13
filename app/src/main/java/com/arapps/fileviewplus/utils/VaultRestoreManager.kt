package com.arapps.fileviewplus.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.util.zip.ZipInputStream

object VaultRestoreManager {

    fun restoreVaultFromZip(context: Context, zipUri: Uri, vaultDir: File) {
        try {
            vaultDir.mkdirs()
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val outFile = File(vaultDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            Toast.makeText(context, "Vault restored from backup!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
