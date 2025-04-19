// File: com/arapps/fileviewplus/notifications/FirebaseTokenLogger.kt

package com.arapps.fileviewplus.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseTokenLogger {
    fun logToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i("FCM_TOKEN", "Token: ${task.result}")
                } else {
                    Log.e("FCM_TOKEN", "Failed to get FCM token", task.exception)
                }
            }
    }
}
