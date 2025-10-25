// File: app/src/main/java/com/arapps/fileviewplus/viewer/ImageViewerActivity.kt
package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.utils.copyFileToVault
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.NotificationUtils
import com.arapps.fileviewplus.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsControllerCompat
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.core.AppGlobals

class ImageViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_PATHS = "paths"
        const val EXTRA_INDEX = "index"
        const val EXTRA_FROM_VAULT = "fromVault"
        const val EXTRA_DELETED_PATH = "deleted_path"
        const val EXTRA_RENAMED_OLD = "renamed_old_path"
        const val EXTRA_RENAMED_NEW = "renamed_new_path"

        // Broadcast actions
        const val ACTION_FILE_DELETED = "com.arapps.fileviewplus.ACTION_FILE_DELETED"
        const val ACTION_FILE_RENAMED = "com.arapps.fileviewplus.ACTION_FILE_RENAMED"

        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean = false) {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_PATH, fileNode.path)
                putExtra(EXTRA_FROM_VAULT, fromVault)
            }
            context.startActivity(intent)
        }

        fun launch(context: Context, files: List<FileNode>, startIndex: Int = 0, fromVault: Boolean = false) {
            val paths = ArrayList<String>(files.map { it.path })
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_PATHS, paths)
                putExtra(EXTRA_INDEX, startIndex)
                putExtra(EXTRA_FROM_VAULT, fromVault)
            }
            context.startActivity(intent)
        }
    }

    private var tempFileForExternalUri: File? = null
    private var isExternalOpen: Boolean = false

    // The list of files we're browsing and current index
    private var filesList: List<FileNode> = emptyList()
    private var startIndex: Int = 0

    // Launcher to ask user for a tree permission if DeletionManager returns NeedUserGrant
    private val pickTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { pickedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) { /* ignore vendor quirks */ }

            // If deletion was pending, deletion flow handled elsewhere; here we do nothing special
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Permission not granted.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure the window background and system bars match the app background to avoid
        // white flashes/areas when using dark theme or when Compose content doesn't fully
        // cover the window (e.g. letterboxing when zoomed out).
        try {
            val bg = AndroidColor.parseColor("#121212")
            window.setBackgroundDrawable(ColorDrawable(bg))
            window.statusBarColor = bg
            window.navigationBarColor = bg
        } catch (_: Exception) { /* ignore on weird OEMs */ }

        // Resolve list of files and start index
        val paths = intent.getStringArrayListExtra(EXTRA_PATHS)
        startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        isExternalOpen = intent.data != null

        filesList = if (paths != null && paths.isNotEmpty()) {
            paths.map { FileNode.fromFile(File(it)) }
        } else {
            val resolved = resolveFileFromIntent(intent)
            if (resolved == null || !resolved.exists() || !resolved.canRead()) {
                Toast.makeText(this, "Cannot open image", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            listOf(FileNode.fromFile(resolved))
        }

        setContent {
            // Read user's theme preference and feed into the Compose theme so the viewer
            // uses the user's chosen dark/light mode instead of defaulting to system.
            val themeFlow = com.arapps.fileviewplus.settings.ThemeSettings.getThemeFlow(applicationContext)
            // Use the same initial/default as MainActivity so there's no incorrect light/dark flash
            val isDarkMode by themeFlow.collectAsState(initial = false)

            // status bar appearance (keep icons readable when theme changes)
            SideEffect {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkMode
            }

            FileFlowPlusTheme(darkTheme = isDarkMode) {
                ImageViewerScreen(
                    files = filesList,
                    initialIndex = startIndex.coerceIn(0, filesList.size - 1),
                    isExternal = isExternalOpen,
                    onDeleteRequested = { node ->
                        lifecycleScope.launchWhenResumed {
                            try {
                                val res = DeletionManager.deleteFile(this@ImageViewerActivity, node)
                                when (res) {
                                    is DeletionManager.DeleteResult.Deleted -> {
                                        notifyDeletedAndFinish(node)
                                    }
                                    is DeletionManager.DeleteResult.NeedUserGrant -> {
                                        runOnUiThread {
                                            Toast.makeText(this@ImageViewerActivity, res.message, Toast.LENGTH_LONG).show()
                                        }
                                        try { pickTreeLauncher.launch(null) } catch (_: Throwable) { pickTreeLauncher.launch(null) }
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
                    onClose = { finish() },
                    onRenameRequested = { node, newName ->
                        val f = File(node.path)
                        val dest = File(f.parentFile, newName)
                        val ok = try { f.renameTo(dest) } catch (_: Exception) { false }
                        if (ok) {
                            // Broadcast rename so other screens can update if they listen
                            val b = Intent(ACTION_FILE_RENAMED).apply {
                                putExtra(EXTRA_RENAMED_OLD, f.path)
                                putExtra(EXTRA_RENAMED_NEW, dest.path)
                            }
                            sendBroadcast(b)
                            // return result with new path
                            val data = Intent().apply { putExtra(EXTRA_RENAMED_NEW, dest.path) }
                            setResult(Activity.RESULT_OK, data)
                            // do not delete; keep viewer open and update path by finishing so parent refreshes
                            finish()
                        } else {
                            Toast.makeText(this@ImageViewerActivity, "Rename failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
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

    // Copy a file into a DocumentFile tree URI (destination folder)
    private suspend fun copyFileToTree(node: FileNode, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val doc = DocumentFile.fromTreeUri(this@ImageViewerActivity, treeUri) ?: return@withContext false
            val src = File(node.path)
            val mime = when (src.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
            val created = doc.createFile(mime, src.name) ?: return@withContext false
            contentResolver.openOutputStream(created.uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerScreen(
    files: List<FileNode>,
    initialIndex: Int = 0,
    isExternal: Boolean,
    onDeleteRequested: (FileNode) -> Unit,
    onClose: () -> Unit,
    onRenameRequested: (FileNode, String) -> Unit
) {
    var index by remember { mutableStateOf(initialIndex.coerceIn(0, files.size - 1)) }
    val file = files.getOrNull(index) ?: return
    val painter = rememberAsyncImagePainter(File(file.path))
    val context = LocalContext.current
    var showConfirmDelete by remember { mutableStateOf(false) }
    // Separate confirm for deleting original after move-to-vault
    var showDeleteAfterMove by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(file.name) }
    val uiScope = rememberCoroutineScope()

    // Folder picker state for Move-to-Vault
    var showMoveToVaultFolderDialog by remember { mutableStateOf(false) }
    var vaultFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVaultFolder by remember { mutableStateOf("") }

    // Resume pending move-to-vault after PIN setup
    LaunchedEffect(Unit) {
        AppGlobals.vaultReady.collect {
            val pending = AppGlobals.pendingMoveToVaultPath
            if (!pending.isNullOrEmpty()) {
                uiScope.launch(Dispatchers.IO) {
                    val destRoot = File(context.filesDir, ".vault").apply { mkdirs() }
                    val copied = try { copyFileToVault(pending, destRoot) } catch (_: Exception) { null }
                    withContext(Dispatchers.Main) {
                        if (copied != null) {
                            Toast.makeText(context, "Moved to Vault", Toast.LENGTH_SHORT).show()
                            try { NotificationUtils.showVaultMovedNotification(context, copied.name) } catch (_: Exception) {}
                            if (!isExternal && pending == file.path) showDeleteAfterMove = true
                        } else {
                            Toast.makeText(context, "Move to Vault failed", Toast.LENGTH_LONG).show()
                        }
                        AppGlobals.pendingMoveToVaultPath = null
                    }
                }
            }
        }
    }

    // scale & pan state for pinch-to-zoom
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // UI visibility (auto-hide) and toggle on tap
    var showUi by remember { mutableStateOf(true) }
    // auto-hide when shown
    LaunchedEffect(showUi) {
        if (showUi) {
            delay(2000)
            showUi = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedVisibility(visible = showUi) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = file.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = StorageStats.formatSize(file.size),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // share
                        IconButton(onClick = {
                            uiScope.launch {
                                val ok = withContext(Dispatchers.IO) { ZipUtils.shareSingleFile(context, file) }
                                if (!ok) withContext(Dispatchers.Main) { Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show() }
                            }
                        }) { Icon(Icons.Default.Share, contentDescription = "Share") }

                        IconButton(onClick = {
                            // zip & share
                            uiScope.launch {
                                try {
                                    val zipFile = withContext(Dispatchers.IO) { ZipUtils.createZip(context, file.name.substringBeforeLast('.'), listOf(file)) }
                                    withContext(Dispatchers.Main) {
                                        if (zipFile != null) ZipUtils.shareZip(context, zipFile) else Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Zipping failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() } }
                            }
                        }) { Icon(Icons.Default.Archive, contentDescription = "Zip & Share") }

                        // Move to Vault (now visible in top bar)
                        IconButton(onClick = {
                            val pin = try { getStoredPin(context) } catch (_: Exception) { null }
                            if (pin.isNullOrEmpty()) {
                                Toast.makeText(context, "Set up a Vault PIN first", Toast.LENGTH_LONG).show()
                                // Remember pending request and navigate to Vault to set PIN
                                AppGlobals.pendingMoveToVaultPath = file.path
                                val i = Intent(context, MainActivity::class.java).apply {
                                    putExtra("navigate_to", "vault")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                context.startActivity(i)
                                return@IconButton
                            }
                            val root = File(context.filesDir, ".vault").apply { mkdirs() }
                            vaultFolders = root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                            selectedVaultFolder = vaultFolders.firstOrNull() ?: ""
                            showMoveToVaultFolderDialog = true
                        }) { Icon(Icons.Default.Lock, contentDescription = "Move to Vault") }

                        IconButton(onClick = {
                            if (isExternal) {
                                Toast.makeText(context, "Cannot delete external file. Please delete it from the source.", Toast.LENGTH_LONG).show()
                                return@IconButton
                            }
                            showConfirmDelete = true
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }

                        // overflow for extra actions
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    expanded = false
                                    showRename = true
                                })
                                // Removed legacy Move to Vault from overflow; now in top bar
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                     detectHorizontalDragGestures { change, dragAmount ->
                         // simple threshold-based swipe only when not zoomed
                         if (scale == 1f) {
                             if (dragAmount > 60) index = (index - 1).coerceAtLeast(0)
                             else if (dragAmount < -60) index = (index + 1).coerceAtMost(files.lastIndex)
                         }
                     }
                 }
         ) {
            // left/right tap areas behind the image so the image can receive gestures first
            Row(modifier = Modifier.matchParentSize()) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { if (scale == 1f) index = (index - 1).coerceAtLeast(0) }
                ) {}
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { if (scale == 1f) index = (index + 1).coerceAtMost(files.lastIndex) }
                ) {}
            }

            // image box on top (receives taps for UI toggle and double-tap zoom)
            Box(modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        // apply zoom/pan
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            ) {
                // Capture taps separately so we can toggle UI and double-tap zoom
                Box(modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showUi = !showUi
                            },
                            onDoubleTap = {
                                // toggle between fit (1f) and a zoom level
                                if (scale > 1f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                                showUi = true
                            }
                        )
                    }
                )

                Image(
                    painter = painter,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                )
            }

            // bottom caption overlay (hide when UI is hidden)
            AnimatedVisibility(visible = showUi) {
                Box(modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))) ) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "${index + 1}/${files.size}", color = Color.White)
                        Text(text = file.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Confirm delete dialog
        if (showConfirmDelete) {
            AlertDialog(
                onDismissRequest = { showConfirmDelete = false },
                title = { Text("Delete file") },
                text = { Text("Are you sure you want to permanently delete \"${file.name}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDelete = false
                        onDeleteRequested(file)
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showConfirmDelete = false }) { Text("Cancel") } }
            )
        }

        // Prompt to delete the original after a successful move-to-vault copy
        if (showDeleteAfterMove) {
            AlertDialog(
                onDismissRequest = { showDeleteAfterMove = false },
                title = { Text("Delete original file?") },
                text = { Text("The file was copied to the Vault. Do you also want to delete the original?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteAfterMove = false
                        onDeleteRequested(file)
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteAfterMove = false }) { Text("Keep") } }
            )
        }

        // Rename dialog
        if (showRename) {
            Dialog(onDismissRequest = { showRename = false }) {
                Surface(shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Rename file", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showRename = false }) { Text("Cancel") }
                            TextButton(onClick = {
                                showRename = false
                                onRenameRequested(file, newName)
                            }) { Text("Rename") }
                        }
                    }
                }
            }
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
                        val dest = if (selectedVaultFolder.isBlank()) root else File(root, selectedVaultFolder)
                        uiScope.launch(Dispatchers.IO) {
                            try { if (!dest.exists()) dest.mkdirs() } catch (_: Exception) {}
                            val copied = copyFileToVault(file.path, dest)
                            withContext(Dispatchers.Main) {
                                if (copied != null) {
                                    Toast.makeText(context, "Moved to Vault", Toast.LENGTH_SHORT).show()
                                    try { NotificationUtils.showVaultMovedNotification(context, copied.name) } catch (_: Exception) {}
                                    if (!isExternal) showDeleteAfterMove = true
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
}
