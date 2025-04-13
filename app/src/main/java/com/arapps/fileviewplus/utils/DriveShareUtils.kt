package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object DriveShareUtils {

    fun shareToDrive(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // This is key — it triggers Google Drive in the share sheet
                putExtra(Intent.EXTRA_TITLE, file.name)
            }

            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Upload Vault to Google Drive"
                )
            )

            Toast.makeText(context, "Select Google Drive to back up your encrypted Vault", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share backup. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }
}
