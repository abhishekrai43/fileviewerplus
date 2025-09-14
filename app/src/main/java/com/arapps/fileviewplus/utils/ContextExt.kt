package com.arapps.fileviewplus.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Safely unwraps an Activity from a Context if present.
 * Returns the Activity or null if not available.
 *
 * Use this from Compose when you have LocalContext.current and want to ensure
 * you pass an Activity into startActivity / viewer launchers.
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
