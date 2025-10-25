package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import android.content.ContentUris
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.copyFileToVault
import com.arapps.fileviewplus.utils.NotificationUtils
import com.arapps.fileviewplus.MainActivity
import com.arapps.fileviewplus.core.AppGlobals

private const val EXTRA_PATH = "extra_path"


class PdfViewerActivity : ComponentActivity() {

    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_PATH, fileNode.path)
                putExtra("fromVault", fromVault)
            }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private val activityScope = CoroutineScope(SupervisorJob())

    // track opened file path and whether source was external
    private var openedPath: String? = null
    private var openedFromExternalUri: Boolean = false

    // Launcher for ACTION_OPEN_DOCUMENT_TREE (for SAF permission)
    private val pickTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            Toast.makeText(this, "Permission not granted. Cannot delete file.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Throwable) {
            // ignore vendor quirks
        }

        // After permission persisted, attempt deletion again
        openedPath?.let { path ->
            activityScope.launch {
                val node = FileNode.fromFile(File(path))
                when (val res = DeletionManager.deleteFile(this@PdfViewerActivity, node)) {
                    is DeletionManager.DeleteResult.Deleted -> {
                        notifyDeletedAndFinish(node)
                    }
                    is DeletionManager.DeleteResult.NeedUserGrant -> {
                        runOnUiThread { Toast.makeText(this@PdfViewerActivity, res.message, Toast.LENGTH_LONG).show() }
                    }
                    is DeletionManager.DeleteResult.Failed -> {
                        runOnUiThread { Toast.makeText(this@PdfViewerActivity, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathExtra = intent?.getStringExtra(EXTRA_PATH)
        val uriFromData = intent?.data

        var resolvedFile: File? = null
        if (!pathExtra.isNullOrBlank()) {
            resolvedFile = File(pathExtra)
            openedFromExternalUri = false
        } else if (uriFromData != null) {
            openedFromExternalUri = true
            resolvedFile = try {
                val input = contentResolver.openInputStream(uriFromData)
                if (input != null) {
                    val tmp = File(cacheDir, "external_pdf_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(tmp).use { out -> input.copyTo(out) }
                    tmp
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        openedPath = resolvedFile?.absolutePath

        setContent {
            MaterialTheme {
                Surface {
                    PdfViewerHost(
                        path = openedPath,
                        dataUri = null,
                        isExternalOpen = openedFromExternalUri,
                        onClose = { finish() },
                        onShare = { filePath -> shareFile(filePath) },
                        onZipAndShare = { filePath -> zipAndShare(filePath) },
                        onDeleteRequested = { filePath -> handleDeleteRequest(filePath) }
                    )
                }
            }
        }
    }

    private fun shareFile(filePath: String) {
        val f = File(filePath)
        if (!f.exists() || !f.canRead()) {
            runOnUiThread { Toast.makeText(this, "Cannot share file", Toast.LENGTH_SHORT).show() }
            return
        }
        activityScope.launch {
            try {
                val success = ZipUtils.shareSingleFile(this@PdfViewerActivity, FileNode.fromFile(f))
                if (!success) runOnUiThread { Toast.makeText(this@PdfViewerActivity, "Sharing failed", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@PdfViewerActivity, "Share error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun zipAndShare(filePath: String) {
        val f = File(filePath)
        if (!f.exists() || !f.canRead()) {
            runOnUiThread { Toast.makeText(this, "Cannot zip file", Toast.LENGTH_SHORT).show() }
            return
        }
        activityScope.launch {
            try {
                val zip = ZipUtils.createZip(this@PdfViewerActivity, f.name.substringBeforeLast('.'), listOf(FileNode.fromFile(f)))
                withContext(Dispatchers.Main) {
                    if (zip != null) ZipUtils.shareZip(this@PdfViewerActivity, zip)
                    else Toast.makeText(this@PdfViewerActivity, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@PdfViewerActivity, "Zipping failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun handleDeleteRequest(filePath: String) {
        if (openedFromExternalUri) {
            Toast.makeText(this, "Cannot delete external file. Please delete it from the source.", Toast.LENGTH_LONG).show()
            return
        }

        activityScope.launch {
            val node = FileNode.fromFile(File(filePath))

            val isMedia = isLikelyMediaFile(filePath)
            if (isMedia) {
                val mediaDeleted = try { attemptMediaStoreDelete(this@PdfViewerActivity, filePath) } catch (_: Throwable) { false }
                if (mediaDeleted) {
                    withContext(Dispatchers.Main) { notifyDeletedAndFinish(node) }
                    return@launch
                }
            }

            val f = File(filePath)
            if (f.exists()) {
                val ok = try { f.delete() } catch (_: Throwable) { false }
                if (ok) {
                    withContext(Dispatchers.Main) { notifyDeletedAndFinish(node) }
                    return@launch
                }
            }

            when (val res = DeletionManager.deleteFile(this@PdfViewerActivity, node)) {
                is DeletionManager.DeleteResult.Deleted -> {
                    withContext(Dispatchers.Main) { notifyDeletedAndFinish(node) }
                }
                is DeletionManager.DeleteResult.NeedUserGrant -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfViewerActivity, res.message, Toast.LENGTH_LONG).show()
                    }
                    try {
                        pickTreeLauncher.launch(null)
                    } catch (t: Throwable) {
                        pickTreeLauncher.launch(null)
                    }
                }
                is DeletionManager.DeleteResult.Failed -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfViewerActivity, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun notifyDeletedAndFinish(deleted: FileNode) {
        val data = Intent().apply { putExtra(EXTRA_DELETED_PATH, deleted.path) }
        setResult(Activity.RESULT_OK, data)

        val b = Intent(ACTION_FILE_DELETED).apply { putExtra(EXTRA_DELETED_PATH, deleted.path) }
        sendBroadcast(b)

        runOnUiThread {
            Toast.makeText(this@PdfViewerActivity, "Deleted ${deleted.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isLikelyMediaFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".3gp") || lower.endsWith(".mov") ||
                lower.endsWith(".mp3") || lower.endsWith(".wav")
    }

    private fun attemptMediaStoreDelete(context: Context, absolutePath: String): Boolean {
        try {
            val cr = context.contentResolver
            if (queryAndDeleteFromStore(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
            if (queryAndDeleteFromStore(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
            if (queryAndDeleteFromStore(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
        } catch (_: Throwable) {}
        return false
    }

    private fun queryAndDeleteFromStore(cr: android.content.ContentResolver, collection: Uri, absolutePath: String): Boolean {
        var cursor: android.database.Cursor? = null
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA)
            val sel = "${MediaStore.MediaColumns.DATA} = ?"
            cursor = cr.query(collection, projection, sel, arrayOf(absolutePath), null)
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, id)
                try {
                    val rows = cr.delete(uri, null, null)
                    return rows > 0
                } catch (se: SecurityException) {
                    return false
                }
            }
            false
        } catch (_: Exception) {
            false
        } finally {
            try { cursor?.close() } catch (_: Exception) {}
        }
    }
}


/**
 * Composable host: renders PDF pages (no changes to core rendering logic).
 * Added in-UI delete confirmation dialog and proper TopAppBar with action icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerHost(
    path: String?,
    dataUri: Uri?,
    isExternalOpen: Boolean = false,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
    onZipAndShare: (String) -> Unit,
    onDeleteRequested: (String) -> Unit
) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }

    // Resolve Uri once from path or dataUri (copy external content into cache)
    LaunchedEffect(path, dataUri) {
        uri = when {
            path != null -> Uri.fromFile(File(path))
            dataUri != null -> {
                try {
                    val input = context.contentResolver.openInputStream(dataUri)
                    if (input != null) {
                        val tmp = File(context.cacheDir, "external_pdf_${System.currentTimeMillis()}.pdf")
                        FileOutputStream(tmp).use { out -> input.copyTo(out) }
                        Uri.fromFile(tmp)
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            else -> null
        }
    }

    // UI state
    var pageCount by remember { mutableStateOf(0) }
    var currentPage by rememberSaveable { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var showDeleteAfterMove by remember { mutableStateOf(false) }
    // Folder chooser state
    var showMoveToVaultFolderDialog by remember { mutableStateOf(false) }
    var vaultFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVaultFolder by remember { mutableStateOf("") }

    // Coroutine scope remembered at composable scope (used by click handlers)
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx().toInt() }

    fun recycleBmp(b: Bitmap?) {
        try { b?.let { if (!it.isRecycled) it.recycle() } } catch (_: Exception) {}
    }

    // Open renderer when uri changes
    LaunchedEffect(uri) {
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        renderer = null
        recycleBmp(bitmap)
        bitmap = null
        pageCount = 0
        currentPage = 0

        val u = uri ?: return@LaunchedEffect

        try {
            val descriptor = withContext(Dispatchers.IO) {
                context.contentResolver.openFileDescriptor(u, "r")
            } ?: return@LaunchedEffect
            pfd = descriptor
            renderer = PdfRenderer(descriptor)
            pageCount = renderer?.pageCount ?: 0
            currentPage = 0

            renderer?.let { r ->
                val bmp = renderPageScaled(r, 0, screenWidthPx)
                bitmap = bmp
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recycleBmp(bitmap)
            bitmap = null
            try { renderer?.close() } catch (_: Exception) {}
            renderer = null
            try { pfd?.close() } catch (_: Exception) {}
            pfd = null
            pageCount = 0
        }
    }

    // Re-render when currentPage changes
    LaunchedEffect(currentPage, renderer) {
        val r = renderer ?: return@LaunchedEffect
        val idx = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val bmp = renderPageScaled(r, idx, screenWidthPx)
        recycleBmp(bitmap)
        bitmap = bmp
    }

    // Resume pending Move-to-Vault after PIN setup
    LaunchedEffect(Unit) {
        AppGlobals.vaultReady.collect {
            val pending = AppGlobals.pendingMoveToVaultPath
            if (!pending.isNullOrEmpty()) {
                val ctx = context
                scope.launch(Dispatchers.IO) {
                    val dest = File(ctx.filesDir, ".vault").apply { mkdirs() }
                    val copied = try { copyFileToVault(pending, dest) } catch (_: Exception) { null }
                    withContext(Dispatchers.Main) {
                        if (copied != null) {
                            Toast.makeText(ctx, "Moved to Vault", Toast.LENGTH_SHORT).show()
                            try { NotificationUtils.showVaultMovedNotification(ctx, copied.name) } catch (_: Exception) {}
                            if (!isExternalOpen && pending == (path ?: uri?.path)) showDeleteAfterMove = true
                        } else {
                            Toast.makeText(ctx, "Move failed", Toast.LENGTH_LONG).show()
                        }
                        AppGlobals.pendingMoveToVaultPath = null
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            recycleBmp(bitmap)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = File(path ?: uri?.path ?: "").name) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(path ?: uri?.path ?: return@IconButton) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { onZipAndShare(path ?: uri?.path ?: return@IconButton) }) {
                        Icon(Icons.Default.Archive, contentDescription = "Zip & Share")
                    }
                    // Move to Vault
                    IconButton(onClick = {
                        val filePath = path ?: uri?.path
                        if (filePath == null) return@IconButton
                        val pin = try { getStoredPin(context) } catch (_: Exception) { null }
                        if (pin.isNullOrEmpty()) {
                            Toast.makeText(context, "Set up a Vault PIN first", Toast.LENGTH_LONG).show()
                            val i = Intent(context, MainActivity::class.java).apply {
                                putExtra("navigate_to", "vault")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            // remember pending
                            AppGlobals.pendingMoveToVaultPath = filePath
                            context.startActivity(i)
                            return@IconButton
                        }
                        // Show vault folder picker
                        val root = File(context.filesDir, ".vault").apply { mkdirs() }
                        vaultFolders = root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                        selectedVaultFolder = vaultFolders.firstOrNull() ?: ""
                        showMoveToVaultFolderDialog = true
                    }) { Icon(Icons.Default.Lock, contentDescription = "Move to Vault") }

                    IconButton(onClick = {
                        val filePath = path ?: uri?.path ?: return@IconButton
                        onDeleteRequested(filePath)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Existing PDF page rendering UI follows (unchanged)
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Text(text = "Unable to load PDF", modifier = Modifier.align(Alignment.Center))
                }

                // Pager controls
                if (pageCount > 1) {
                    Column(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (currentPage > 0) currentPage-- }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
                            }
                            Text("${currentPage + 1} / $pageCount", modifier = Modifier.padding(horizontal = 12.dp))
                            IconButton(onClick = { if (currentPage < pageCount - 1) currentPage++ }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { currentPage = it.toInt() },
                            valueRange = 0f..((pageCount - 1).coerceAtLeast(0)).toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAfterMove) {
        AlertDialog(
            onDismissRequest = { showDeleteAfterMove = false },
            title = { Text("Delete original file?") },
            text = { Text("The file was copied to the Vault. Do you also want to delete the original?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAfterMove = false
                    val filePath = path ?: uri?.path
                    if (filePath != null) onDeleteRequested(filePath)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAfterMove = false }) { Text("Keep") } }
        )
    }

    // Vault folder picker dialog
    if (showMoveToVaultFolderDialog) {
        val root = File(context.filesDir, ".vault").apply { mkdirs() }
        AlertDialog(
            onDismissRequest = { showMoveToVaultFolderDialog = false },
            title = { Text("Move to Vault") },
            text = {
                Column {
                    if (vaultFolders.isEmpty()) {
                        Text("No folders in vault. The file will be placed in the vault root. You can create folders in the Vault screen.")
                    } else {
                        Text("Choose a destination folder:")
                        Spacer(Modifier.height(8.dp))
                        vaultFolders.forEach { name ->
                            Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                androidx.compose.material3.RadioButton(
                                    selected = selectedVaultFolder == name,
                                    onClick = { selectedVaultFolder = name }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMoveToVaultFolderDialog = false
                    val filePath = path ?: uri?.path ?: return@TextButton
                    val dest = if (selectedVaultFolder.isBlank()) root else File(root, selectedVaultFolder)
                    scope.launch(Dispatchers.IO) {
                        try { if (!dest.exists()) dest.mkdirs() } catch (_: Exception) {}
                        val copied = copyFileToVault(filePath, dest)
                        withContext(Dispatchers.Main) {
                            if (copied != null) {
                                Toast.makeText(context, "Moved to Vault", Toast.LENGTH_SHORT).show()
                                try { NotificationUtils.showVaultMovedNotification(context, copied.name) } catch (_: Exception) {}
                                if (!isExternalOpen) showDeleteAfterMove = true
                            } else {
                                Toast.makeText(context, "Move failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { showMoveToVaultFolderDialog = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Render a page to a Bitmap scaled to targetWidth (keeps aspect ratio).
 * Runs on Dispatchers.IO when called from a coroutine.
 */
private suspend fun renderPageScaled(renderer: PdfRenderer, pageIndex: Int, targetWidth: Int): Bitmap? {
    return withContext(Dispatchers.IO) {
        var page: PdfRenderer.Page? = null
        try {
            page = renderer.openPage(pageIndex)
            val origW = page.width
            val origH = page.height
            val targetW = if (origW > targetWidth && targetWidth > 0) targetWidth else origW
            val scale = targetW.toFloat() / origW.toFloat()
            val targetH = (origH * scale).toInt().coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
        }
    }
}
