// File: app/src/main/java/com/arapps/fileviewplus/viewer/ImageViewerActivity.kt
package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImageViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_FROM_VAULT = "fromVault"
        const val EXTRA_DELETED_PATH = "deleted_path"

        // Broadcast action sent when a file is deleted from the viewer
        const val ACTION_FILE_DELETED = "com.arapps.fileviewplus.ACTION_FILE_DELETED"

        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean = false) {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_PATH, fileNode.path)
                putExtra(EXTRA_FROM_VAULT, fromVault)
            }
            context.startActivity(intent)
        }
    }

    private var tempFileForExternalUri: File? = null
    private var isExternalOpen: Boolean = false
    private var fileNode: FileNode? = null

    // Launcher to ask user for a tree permission if DeletionManager returns NeedUserGrant
    private val pickTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { pickedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) { /* ignore vendor quirks */ }

            // After permission is taken, retry deletion automatically.
            fileNode?.let { node ->
                // Launch a background coroutine to delete
                lifecycleScope.launchWhenResumed {
                    try {
                        val res = DeletionManager.deleteFile(this@ImageViewerActivity, node)
                        when (res) {
                            is DeletionManager.DeleteResult.Deleted -> {
                                notifyDeletedAndFinish(node)
                            }
                            is DeletionManager.DeleteResult.Failed -> {
                                runOnUiThread {
                                    Toast.makeText(this@ImageViewerActivity, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show()
                                }
                            }
                            is DeletionManager.DeleteResult.NeedUserGrant -> {
                                runOnUiThread {
                                    Toast.makeText(this@ImageViewerActivity, res.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        runOnUiThread {
                            Toast.makeText(this@ImageViewerActivity, "Delete error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "Permission not granted. Cannot delete file.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resolved = resolveFileFromIntent(intent)
        if (resolved == null || !resolved.exists() || !resolved.canRead()) {
            Toast.makeText(this, "Cannot open image", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fileNode = FileNode.fromFile(resolved)
        isExternalOpen = intent.data != null

        setContent {
            FileFlowPlusTheme {
                fileNode?.let { node ->
                    ImageViewerScreen(
                        file = node,
                        isExternal = isExternalOpen,
                        onDeleteRequested = { // called when user confirms delete
                            // start deletion flow
                            lifecycleScope.launchWhenResumed {
                                try {
                                    val res = DeletionManager.deleteFile(this@ImageViewerActivity, node)
                                    when (res) {
                                        is DeletionManager.DeleteResult.Deleted -> {
                                            notifyDeletedAndFinish(node)
                                        }
                                        is DeletionManager.DeleteResult.NeedUserGrant -> {
                                            // If permission is needed, launch the picker with suggested URI (if any)
                                            val suggested = res.suggestedUriToOpen
                                            runOnUiThread {
                                                Toast.makeText(this@ImageViewerActivity, res.message, Toast.LENGTH_LONG).show()
                                            }
                                            try {
                                                val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                                    suggested?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                                                }
                                                pickTreeLauncher.launch(null) // pass null; extras may not be reliable across devices
                                            } catch (t: Throwable) {
                                                pickTreeLauncher.launch(null)
                                            }
                                        }
                                        is DeletionManager.DeleteResult.Failed -> {
                                            runOnUiThread {
                                                Toast.makeText(this@ImageViewerActivity, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    runOnUiThread {
                                        Toast.makeText(this@ImageViewerActivity, "Delete error: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            tempFileForExternalUri?.delete()
        } catch (_: Exception) { /* ignore */ }
        super.onDestroy()
    }

    /**
     * Resolve file from path or copy external content URI to cache.
     */
    private fun resolveFileFromIntent(intent: Intent): File? {
        intent.getStringExtra(EXTRA_PATH)?.let { return File(it) }
        val uri: Uri = intent.data ?: return null
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val mime = contentResolver.getType(uri)
            val ext = mime?.substringAfterLast("/") ?: uri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
            val tempFile = File(cacheDir, "external_img_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
            tempFileForExternalUri = tempFile
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * When deleted successfully, notify both via Activity result and a broadcast for any screens to update immediately.
     */
    private fun notifyDeletedAndFinish(deleted: FileNode) {
        // Set result for ActivityResult APIs
        val data = Intent().apply { putExtra(EXTRA_DELETED_PATH, deleted.path) }
        setResult(Activity.RESULT_OK, data)

        // Send a broadcast so any screen / fragment can react immediately and update counts.
        val b = Intent(ACTION_FILE_DELETED).apply { putExtra(EXTRA_DELETED_PATH, deleted.path) }
        sendBroadcast(b)

        runOnUiThread { Toast.makeText(this, "Deleted ${deleted.name}", Toast.LENGTH_SHORT).show() }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerScreen(
    file: FileNode,
    isExternal: Boolean,
    onDeleteRequested: () -> Unit,
    onClose: () -> Unit
) {
    val painter = rememberAsyncImagePainter(File(file.path))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showConfirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // Use explicit colors from your MaterialTheme so that dark mode is respected
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = { onClose() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Share
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val ok = ZipUtils.shareSingleFile(context, file)
                            if (!ok) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                    }

                    // Zip & Share (archive icon)
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val zipFile = ZipUtils.createZip(context, file.name.substringBeforeLast('.'), listOf(file))
                                withContext(Dispatchers.Main) {
                                    if (zipFile != null) {
                                        ZipUtils.shareZip(context, zipFile)
                                    } else {
                                        Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Zipping failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Archive, contentDescription = "Zip & Share")
                    }

                    // Delete
                    IconButton(onClick = {
                        if (isExternal) {
                            Toast.makeText(context, "Cannot delete external file. Please delete it from the source.", Toast.LENGTH_LONG).show()
                            return@IconButton
                        }
                        showConfirmDelete = true
                    }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )

            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Image(
                painter = painter,
                contentDescription = "Image Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete file") },
            text = { Text("Are you sure you want to permanently delete \"${file.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    onDeleteRequested()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
