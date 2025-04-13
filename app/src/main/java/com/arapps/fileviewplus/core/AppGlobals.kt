package com.arapps.fileviewplus.core

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File

object AppGlobals {
    lateinit var folderAccessLauncher: ActivityResultLauncher<Intent>
    var fileToDelete: File? = null
}
