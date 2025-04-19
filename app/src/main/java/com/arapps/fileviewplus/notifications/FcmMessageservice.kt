// File: com/arapps/fileviewplus/notifications/FcmMessageService.kt

package com.arapps.fileviewplus.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arapps.fileviewplus.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmMessageService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i("FCM_RECEIVED", "Message data: ${message.data}")

        val title = message.notification?.title ?: "Vault Reminder"
        val body = message.notification?.body ?: "You have a reminder."

        showNotification(title, body)
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "vault_reminders"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Vault Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_reminder_mdpi) // Replace with real icon
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
