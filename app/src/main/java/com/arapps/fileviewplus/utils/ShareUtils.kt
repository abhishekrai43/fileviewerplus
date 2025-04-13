// File: com/arapps/fileviewplus/utils/ShareUtils.kt

package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast

fun shareApp(context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "🔥 Check out FileFlow Plus! A powerful file manager with local sharing and full control. https://play.google.com/store/apps/details?id=${context.packageName}"
        )
    }

    try {
        context.startActivity(Intent.createChooser(shareIntent, "Share FileFlow Plus via"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to share", Toast.LENGTH_SHORT).show()
    }
}
