// File: app/src/main/java/com/arapps/fileviewplus/utils/SafUtils.kt
package com.arapps.fileviewplus.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.arapps.fileviewplus.model.FileNode
import java.io.File

object SafUtils {

    /**
     * Detect whether a file is located in Android-restricted folders that need SAF:
     * Examples: /Android/data/, /Android/obb/
     */
    fun isSafProtected(file: FileNode): Boolean {
        val cleanPath = file.path.lowercase()
        return cleanPath.contains("/android/data/") ||
                cleanPath.contains("/android/obb/")
    }

    /**
     * Returns true if the file is likely safe to delete directly using java.io.File API.
     */
    fun canDeleteOrRenameDirectly(file: FileNode): Boolean {
        return !isSafProtected(file)
    }

    /**
     * Given a persisted tree Uri and an absolute file path, try to resolve a DocumentFile
     * pointing at the exact file.
     *
     * Persisted tree uris look like: content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata
     *
     * Returns null if resolution fails.
     */
    fun findDocumentFileForPath(context: Context, treeUri: Uri, absolutePath: String): DocumentFile? {
        try {
            val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return null

            // Convert absolute path to the document path within the tree
            val treeBase = treeUri.path ?: return null
            // Build a path portion relative to the tree root. Example: for /storage/emulated/0/Android/data/com.foo/files/x.txt
            // if the tree was primary:Android/data then relative would be com.foo/files/x.txt
            val relative = buildRelativePathFromTree(context, treeUri, absolutePath) ?: return null

            var node: DocumentFile? = docFile
            // walk segments and findFile progressively (DocumentFile.findFile requires exact name matches per level)
            relative.split("/").filter { it.isNotEmpty() }.forEach { seg ->
                node = node?.findFile(seg)
            }
            return node
        } catch (t: Throwable) {
            return null
        }
    }

    /**
     * Heuristic: does this persisted tree URI potentially cover the given absolute path?
     * This checks if the treeUri (decoded) appears inside the absolute path.
     */
    fun doesTreeUriCoverPath(context: Context, treeUri: Uri, absolutePath: String): Boolean {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val treeTitle = treeDoc.name ?: return false

        // Quick heuristic: check if the absolute path contains the tree name e.g. "/Android/data"
        val ap = absolutePath.lowercase()
        val t = treeTitle.lowercase()
        return ap.contains(t)
    }

    /**
     * Return a Uri suggestion to open with ACTION_OPEN_DOCUMENT_TREE when we want the user
     * to grant permission for a folder near the absolute file path.
     *
     * On Android we cannot pre-select arbitrary subfolders reliably across vendors, but the returned Uri
     * will be a best-effort (for example: DocumentsContract.buildTreeDocumentUri).
     *
     * We return null if we cannot craft a suggestion.
     */
    fun suggestTreeUriForPath(context: Context, absolutePath: String): Uri? {
        // Try to pick /Android/data or immediate parent folder as a good suggestion
        val lower = absolutePath.lowercase()
        return when {
            lower.contains("/android/data/") -> {
                // Try to return a URI representing primary:Android/data
                // Note: this is a heuristic only — ACTION_OPEN_DOCUMENT_TREE will still require the user to confirm.
                // Build a tree URI for "primary:Android/data"
                val encoded = "primary:Android/data"
                // Build a pseudo tree Uri — some devices accept a DocumentsContract-style URI
                Uri.parse("content://com.android.externalstorage.documents/tree/${Uri.encode(encoded)}")
            }
            lower.contains("/android/obb/") -> {
                val encoded = "primary:Android/obb"
                Uri.parse("content://com.android.externalstorage.documents/tree/${Uri.encode(encoded)}")
            }
            else -> {
                // fallback to root external storage suggestion
                Uri.parse("content://com.android.externalstorage.documents/tree/${Uri.encode("primary:")}")
            }
        }
    }

    /**
     * Build a path relative to a given treeUri for the absolutePath.
     * Returns null if cannot compute.
     *
     * Example:
     *  treeUri -> tree/primary%3AAndroid%2Fdata  (represents primary:Android/data)
     *  absolutePath -> /storage/emulated/0/Android/data/com.foo/files/x.txt
     *  returns -> com.foo/files/x.txt
     */
    fun buildRelativePathFromTree(context: Context, treeUri: Uri, absolutePath: String): String? {
        try {
            val encoded = treeUri.lastPathSegment ?: return null
            // lastPathSegment generally like "primary:Android%2Fdata" or "primary:Android%2Fdata%2Fcom.foo"
            val decoded = Uri.decode(encoded) // e.g. "primary:Android/data"
            val afterColon = decoded.substringAfter(':', "")
            if (afterColon.isEmpty()) return null

            // Find index of afterColon inside path
            val normalizedPath = File(absolutePath).absolutePath.replace("\\", "/")
            val idx = normalizedPath.indexOf(afterColon)
            if (idx == -1) return null
            val rel = normalizedPath.substring(idx + afterColon.length)
            return rel.trimStart('/')
        } catch (t: Throwable) {
            return null
        }
    }
}
