package com.arapps.fileviewplus.utils

import com.arapps.fileviewplus.model.FileNode

object SafUtils {

    /**
     * Detects whether a file is located in a restricted Android folder
     * such as /Android/data or /Android/obb — which require SAF access.
     */
    fun isSafProtected(file: FileNode): Boolean {
        val cleanPath = file.path.lowercase()
        return cleanPath.contains("/android/data/") ||
                cleanPath.contains("/android/obb/")
    }

    /**
     * Optional: returns true if the file is likely modifiable (rename/delete)
     */
    fun canDeleteOrRenameDirectly(file: FileNode): Boolean {
        return !isSafProtected(file)
    }
}
