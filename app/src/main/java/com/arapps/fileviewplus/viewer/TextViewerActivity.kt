package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.utils.copyFileToVault
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.NotificationUtils
import com.arapps.fileviewplus.MainActivity
import com.arapps.fileviewplus.viewer.ImageViewerActivity.Companion.ACTION_FILE_DELETED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


private const val EXTRA_DELETED_PATH = "deleted_path"

class TextViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("path")
        if (path.isNullOrBlank()) {
            finish()
            return
        }

        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TextViewerHost(file = file, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, TextViewerActivity::class.java).apply {
                putExtra("path", fileNode.path)
                putExtra("fromVault", fromVault)
            }
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerHost(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Read file content (keeps previous behavior)
    val textState by produceState(initialValue = "Loading...", file) {
        value = try {
            withContext(Dispatchers.IO) { file.readText() }
        } catch (e: Exception) {
            "Unable to read file: ${e.localizedMessage ?: e.message}"
        }
    }

    // Dialog state for delete confirmation
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // After moving to vault, confirm deleting original
    var showDeleteAfterMove by rememberSaveable { mutableStateOf(false) }

    // Launcher for ACTION_OPEN_DOCUMENT_TREE if SAF permission is needed
    val pickTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(context, "Permission not granted. Cannot delete ${file.name}", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        // Persist permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Throwable) {
            // ignore vendor quirks
        }

        // After obtaining permission, attempt deletion again automatically
        coroutineScope.launch {
            val res = attemptDeleteFlow(context, file)
            when (res) {
                is DeleteResult.Deleted -> {
                    // notify and finish
                    (context as? Activity)?.let { act ->
                        val data = Intent().apply { putExtra(EXTRA_DELETED_PATH, file.absolutePath) }
                        act.setResult(Activity.RESULT_OK, data)
                    }
                    // broadcast
                    val b = Intent(ACTION_FILE_DELETED).apply {
                        putExtra(EXTRA_DELETED_PATH, file.absolutePath)
                    }

                    try { context.sendBroadcast(b) } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finish()
                    }
                }
                is DeleteResult.NeedUserGrant -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                }
                is DeleteResult.Failed -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Share
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val success = try {
                                ZipUtils.shareSingleFile(context, FileNode.fromFile(file))
                            } catch (e: Exception) { false }
                            if (!success) {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    // Zip & Share
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                val zip = ZipUtils.createZip(context, file.name.substringBeforeLast('.'), listOf(FileNode.fromFile(file)))
                                withContext(Dispatchers.Main) {
                                    if (zip != null) ZipUtils.shareZip(context, zip)
                                    else Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Zipping failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Archive, contentDescription = "Zip & Share")
                    }

                    // Move to Vault
                    IconButton(onClick = {
                        val pin = try { getStoredPin(context) } catch (_: Exception) { null }
                        if (pin.isNullOrEmpty()) {
                            Toast.makeText(context, "Set up a Vault PIN first", Toast.LENGTH_LONG).show()
                            val i = Intent(context, MainActivity::class.java).apply {
                                putExtra("navigate_to", "vault")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(i)
                            return@IconButton
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            val dest = File(context.filesDir, ".vault").apply { mkdirs() }
                            val copied = copyFileToVault(file.absolutePath, dest)
                            withContext(Dispatchers.Main) {
                                if (copied != null) {
                                    Toast.makeText(context, "Moved to Vault", Toast.LENGTH_SHORT).show()
                                    try { NotificationUtils.showVaultMovedNotification(context, copied.name) } catch (_: Exception) {}
                                    showDeleteAfterMove = true
                                } else {
                                    Toast.makeText(context, "Move failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Lock, contentDescription = "Move to Vault")
                    }

                    // Delete
                    IconButton(onClick = {
                        showDeleteDialog = true
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text = textState)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete file") },
            text = { Text("Are you sure you want to permanently delete \"${file.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    coroutineScope.launch {
                        val res = attemptDeleteFlow(context, file)
                        when (res) {
                            is DeleteResult.Deleted -> {
                                // Notify lists and finish activity
                                (context as? Activity)?.let { act ->
                                    val data = Intent().apply { putExtra(EXTRA_DELETED_PATH, file.absolutePath) }
                                    act.setResult(Activity.RESULT_OK, data)
                                }
                                val b = Intent(ACTION_FILE_DELETED).apply { putExtra(EXTRA_DELETED_PATH, file.absolutePath) }
                                try { context.sendBroadcast(b) } catch (_: Exception) {}
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                                    (context as? Activity)?.finish()
                                }
                            }
                            is DeleteResult.NeedUserGrant -> {
                                // Launch picker with suggested URI if available
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                                try {
                                    pickTreeLauncher.launch(res.suggestedUriToOpen)
                                } catch (_: Throwable) {
                                    pickTreeLauncher.launch(null)
                                }
                            }
                            is DeleteResult.Failed -> {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Confirm delete after move-to-vault
    if (showDeleteAfterMove) {
        AlertDialog(
            onDismissRequest = { showDeleteAfterMove = false },
            title = { Text("Delete original file?") },
            text = { Text("The file was copied to the Vault. Do you also want to delete the original?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAfterMove = false
                    coroutineScope.launch {
                        val res = attemptDeleteFlow(context, file)
                        if (res is DeleteResult.Deleted) {
                            (context as? Activity)?.finish()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAfterMove = false }) { Text("Keep") } }
        )
    }
}

/** Local small result sealed class mirroring DeletionManager.DeleteResult */
private sealed class DeleteResult {
    object Deleted : DeleteResult()
    data class NeedUserGrant(val suggestedUriToOpen: Uri?, val message: String) : DeleteResult()
    data class Failed(val reason: String) : DeleteResult()
}

/**
 * Attempt deletion using direct file delete -> DeletionManager (which handles SAF & media store heuristics).
 * Returns DeleteResult to caller.
 */
private suspend fun attemptDeleteFlow(context: Context, file: File): DeleteResult {
    return withContext(Dispatchers.IO) {
        try {
            // Fast path: direct delete
            if (file.exists()) {
                val directOk = try { file.delete() } catch (t: Throwable) { false }
                if (directOk) return@withContext DeleteResult.Deleted
            }

            // Delegate to DeletionManager which will attempt SAF paths and indicate NeedUserGrant when necessary
            val node = FileNode.fromFile(file)
            when (val dm = DeletionManager.deleteFile(context, node)) {
                is DeletionManager.DeleteResult.Deleted -> return@withContext DeleteResult.Deleted
                is DeletionManager.DeleteResult.NeedUserGrant -> {
                    return@withContext DeleteResult.NeedUserGrant(dm.suggestedUriToOpen, dm.message)
                }
                is DeletionManager.DeleteResult.Failed -> return@withContext DeleteResult.Failed(dm.reason)
            }
        } catch (ex: Exception) {
            return@withContext DeleteResult.Failed(ex.localizedMessage ?: ex.toString())
        }
        return@withContext DeleteResult.Failed("Unknown error while deleting")
    }
}
