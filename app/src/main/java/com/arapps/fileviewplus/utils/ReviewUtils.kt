package com.arapps.fileviewplus.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

object ReviewUtils {
    suspend fun launchReviewFlow(context: Context) {
        val manager = ReviewManagerFactory.create(context)
        val request = try {
            manager.requestReviewFlow().await()
        } catch (e: Exception) {
            null
        }

        if (request != null) {
            manager.launchReviewFlow(context as Activity, request)
        } else {
            openPlayStoreFallback(context)
        }
    }

    private fun openPlayStoreFallback(context: Context) {
        val packageName = context.packageName
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if Play Store is not available
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
