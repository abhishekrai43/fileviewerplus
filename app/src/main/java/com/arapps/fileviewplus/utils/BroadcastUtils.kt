// app/src/main/java/com/arapps/fileviewplus/utils/BroadcastUtils.kt
package com.arapps.fileviewplus.utils

import android.content.Context
import android.content.Intent
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH

fun broadcastFileDeleted(context: Context, path: String) {
    val i = Intent(ACTION_FILE_DELETED).apply {
        putExtra(EXTRA_DELETED_PATH, path)
    }
    context.sendBroadcast(i)
}
