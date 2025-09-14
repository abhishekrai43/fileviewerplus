package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.findActivity

private const val TAG = "ViewerComposeHelper"

/**
 * Call this from Compose to open a FileNode using the correct Activity context when available.
 * Example usage inside a lambda: openFileSafe(fileNode, fromVault = false)
 */
@Composable
fun openFileSafeComposable(fileNode: FileNode, fromVault: Boolean) {
    val ctx = LocalContext.current
    openFileSafe(ctx, fileNode, fromVault)
}

/**
 * Non-Compose helper (kept for completeness) — will prefer an Activity if available.
 */
fun openFileSafe(context: Context, fileNode: FileNode, fromVault: Boolean) {
    // If the given context is wrapped (e.g., LocalContext.current), try to find the Activity
    val activity = context.findActivity()
    if (activity != null) {
        Log.d(TAG, "openFileSafe: launching viewer with Activity context (${activity::class.java.simpleName})")
        ViewerRouter.openFile(activity, fileNode, fromVault)
    } else {
        Log.w(TAG, "openFileSafe: Activity not found. Launching with provided context (${context.javaClass.simpleName}). Viewer may start in a new task.")
        ViewerRouter.openFile(context, fileNode, fromVault)
    }
}
