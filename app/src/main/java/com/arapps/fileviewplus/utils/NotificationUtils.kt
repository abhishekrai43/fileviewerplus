package com.arapps.fileviewplus.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.arapps.fileviewplus.R
import com.arapps.fileviewplus.MainActivity

object NotificationUtils {
    private const val CHANNEL_ID = "file_sharing_channel"
    private const val NOTIFICATION_ID = 101

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Sharing"
            val description = "Notifications for the file sharing server"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showServerRunningNotification(context: Context, ipAddress: String, port: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val scheme = if (port == 2121) "ftp" else "http"
        val url = "$scheme://$ipAddress:$port"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_server) // your icon in res/drawable
            .setContentTitle("📡 Local File Sharing")
            .setContentText("Access at $url")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelServerNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    // ---------------- Vault notifications ----------------
    private const val VAULT_CHANNEL_ID = "vault_ops_channel"

    private fun ensureVaultChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(VAULT_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    VAULT_CHANNEL_ID,
                    "Vault Operations",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for vault actions"
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun showVaultMovedNotification(context: Context, fileName: String) {
        ensureVaultChannel(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openVaultIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vault")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(context, 2025, openVaultIntent, flags)

        val notif = NotificationCompat.Builder(context, VAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder_mdpi)
            .setContentTitle("Moved to Vault")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        // Use a semi-unique ID per file to avoid overwriting different notifications
        val id = 200000 + (fileName.hashCode() and 0x7fffffff) % 100000
        mgr.notify(id, notif)
    }
}
