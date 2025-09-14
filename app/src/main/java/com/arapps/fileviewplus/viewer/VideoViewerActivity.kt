package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.viewer.ImageViewerActivity.Companion.EXTRA_PATH
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit


/**
 * Gallery-style Video Viewer:
 * - Compose-based overlays (auto-hide) over an ExoPlayer-backed PlayerView.
 * - Thumbnail strip of siblings (folder) at bottom; click to jump to that video.
 * - Big playback controls (prev/next/play/pause) in center overlay.
 * - Share / Zip & Share / Delete actions in top bar.
 *
 * Notes:
 * - Delete uses same robust flow (MediaStore -> direct -> DeletionManager -> request tree).
 * - Thumbnails are generated with MediaMetadataRetriever on IO thread.
 * - This keeps ExoPlayer for playback reliability but provides a polished custom UI.
 */
class VideoViewerActivity : ComponentActivity() {

    private var tempCopiedForExternal: File? = null
    private var openedFromExternal = false
    private var openedPath: String? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, VideoViewerActivity::class.java).apply {
                putExtra(EXTRA_PATH, fileNode.path)
                putExtra("fromVault", fromVault)
            }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // immersive (edge-to-edge) experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val resolved = resolveFileFromIntent(intent)
        if (resolved == null || !resolved.exists() || !resolved.canRead()) {
            Toast.makeText(this, "Cannot play video", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        openedPath = resolved.absolutePath
        openedFromExternal = intent.getStringExtra(EXTRA_PATH) == null

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GalleryVideoViewer(
                        startPath = openedPath!!,
                        isExternal = openedFromExternal,
                        onClose = { finish() },
                        onShare = { path -> shareFile(path) },
                        onZip = { path -> zipAndShare(path) },
                        onDelete = { path -> handleDelete(path) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        try { tempCopiedForExternal?.delete() } catch (_: Exception) {}
    }

    private fun resolveFileFromIntent(intent: Intent): File? {
        intent.getStringExtra(EXTRA_PATH)?.let { return File(it) }
        val uri = intent.data ?: return null
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val mime = contentResolver.getType(uri)
            val ext = mime?.substringAfterLast("/") ?: "mp4"
            val tmp = File(cacheDir, "external_vid_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tmp).use { out -> input.copyTo(out) }
            tempCopiedForExternal = tmp
            tmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // share wrapper
    private fun shareFile(path: String) {
        val f = File(path)
        if (!f.exists() || !f.canRead()) {
            Toast.makeText(this, "Cannot share file", Toast.LENGTH_SHORT).show()
            return
        }
        activityScope.launch {
            val ok = ZipUtils.shareSingleFile(this@VideoViewerActivity, FileNode.fromFile(f))
            if (!ok) withContext(Dispatchers.Main) { Toast.makeText(this@VideoViewerActivity, "Share failed", Toast.LENGTH_SHORT).show() }
        }
    }

    // zip&share wrapper
    private fun zipAndShare(path: String) {
        val f = File(path)
        if (!f.exists() || !f.canRead()) {
            Toast.makeText(this, "Cannot zip file", Toast.LENGTH_SHORT).show()
            return
        }
        activityScope.launch {
            val zip = ZipUtils.createZip(this@VideoViewerActivity, f.name.substringBeforeLast('.'), listOf(FileNode.fromFile(f)))
            withContext(Dispatchers.Main) {
                if (zip != null) ZipUtils.shareZip(this@VideoViewerActivity, zip)
                else Toast.makeText(this@VideoViewerActivity, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // delete flow same as before
    private fun handleDelete(path: String) {
        if (openedFromExternal) {
            Toast.makeText(this, "Cannot delete external file. Please delete it from the source.", Toast.LENGTH_LONG).show()
            return
        }

        activityScope.launch {
            val node = FileNode.fromFile(File(path))

            // 1) MediaStore
            val mediaDeleted = try { attemptMediaStoreDelete(this@VideoViewerActivity, path) } catch (_: Throwable) { false }
            if (mediaDeleted) {
                withContext(Dispatchers.Main) { notifyDeletedAndFinish(node) }
                return@launch
            }

            // 2) direct file
            val f = File(path)
            if (f.exists()) {
                val ok = try { f.delete() } catch (_: Throwable) { false }
                if (ok) { withContext(Dispatchers.Main) { notifyDeletedAndFinish(node) }; return@launch }
            }

            // 3) SAF
            when (val res = DeletionManager.deleteFile(this@VideoViewerActivity, node)) {
                is DeletionManager.DeleteResult.Deleted -> withContext(Dispatchers.Main) { notifyDeletedAndFinish(node) }
                is DeletionManager.DeleteResult.NeedUserGrant -> {
                    withContext(Dispatchers.Main) { Toast.makeText(this@VideoViewerActivity, res.message, Toast.LENGTH_LONG).show() }
                    try {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        startActivityForResult(i, 8791)
                    } catch (_: Throwable) {}
                }
                is DeletionManager.DeleteResult.Failed -> withContext(Dispatchers.Main) { Toast.makeText(this@VideoViewerActivity, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun notifyDeletedAndFinish(deleted: FileNode) {
        val data = Intent().apply { putExtra(EXTRA_DELETED_PATH, deleted.path) }
        setResult(Activity.RESULT_OK, data)

        // broadcast using the shared constants
        val b = Intent(ACTION_FILE_DELETED).apply {
            putExtra(EXTRA_DELETED_PATH, deleted.path)
        }
        try { sendBroadcast(b) } catch (_: Exception) {}

        runOnUiThread {
            Toast.makeText(this@VideoViewerActivity, "Deleted ${deleted.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun attemptMediaStoreDelete(context: Context, absolutePath: String): Boolean {
        try {
            val cr = context.contentResolver
            if (queryAndDeleteFromStore(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
            if (queryAndDeleteFromStore(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
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
        } catch (_: Exception) { false } finally { try { cursor?.close() } catch (_: Exception) {} }
    }
}

/* ------------------- Compose gallery UI ------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryVideoViewer(
    startPath: String,
    isExternal: Boolean,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
    onZip: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val startFile = remember(startPath) { File(startPath) }

    // Build sibling list (files in same folder with video-like extensions)
    val siblings by remember(startFile) {
        mutableStateOf(
            startFile.parentFile
                ?.listFiles { f -> f.isFile && isVideoExt(f.name) }
                ?.sortedBy { it.name }
                ?: listOf(startFile)
        )
    }

    // index of currently playing file in siblings
    var currentIndex by remember { mutableStateOf(siblings.indexOfFirst { it.absolutePath == startPath }.coerceAtLeast(0)) }
    val currentFile = siblings.getOrNull(currentIndex) ?: startFile

    // ExoPlayer + PlayerView managed inside Compose host
    val exo = remember(currentFile.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(currentFile)))
            prepare()
            playWhenReady = true
        }
    }

    val playerView = remember(currentFile.absolutePath) {
        PlayerView(context).apply {
            player = exo
            useController = false // hide native controls; we provide custom overlay
            keepScreenOn = true
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    DisposableEffect(currentFile.absolutePath) {
        onDispose {
            try { playerView.player = null } catch (_: Exception) {}
            try { exo.release() } catch (_: Exception) {}
        }
    }

    // UI overlay visibility with auto-hide
    var overlaysVisible by remember { mutableStateOf(true) }
    val overlayAlpha by animateColorAsState(
        targetValue = if (overlaysVisible) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
        animationSpec = tween(durationMillis = 250)
    )

    // auto-hide timer
    val autoHideDelayMs = 3000L
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    fun scheduleAutoHide(scope: CoroutineScope) {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(autoHideDelayMs)
            overlaysVisible = false
        }
    }

    val coroutineScope = rememberCoroutineScope()
    // show overlays initially and schedule hide
    LaunchedEffect(currentFile.absolutePath) {
        overlaysVisible = true
        scheduleAutoHide(coroutineScope)
    }

    // Playback state tracking for Compose controls
    var isPlaying by remember { mutableStateOf(true) }
    LaunchedEffect(exo) {
        val listener = object : Player.Listener {}
        exo.addListener(listener)
        // small loop to update play state
        while (true) {
            isPlaying = exo.isPlaying
            delay(200)
        }
    }

    // Seek state
    val durationMs = exo.duration.takeIf { it > 0 } ?: 0L
    var seekPosition by remember { mutableStateOf(0L) }

    // Thumbnail cache
    val thumbs = remember { mutableStateMapOf<String, Bitmap?>() }

    // Function to generate thumbnail if not present
    fun ensureThumb(path: String) {
        if (thumbs.containsKey(path)) return
        thumbs[path] = null // placeholder while loading
        coroutineScope.launch(Dispatchers.IO) {
            val bmp = generateVideoFrame(path)
            withContext(Dispatchers.Main) { thumbs[path] = bmp }
        }
    }

    // Preload thumbs for visible siblings
    LaunchedEffect(siblings) {
        siblings.take(10).forEach { ensureThumb(it.absolutePath) }
    }

    // Tap handler toggles overlays and resets auto-hide
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = {
            overlaysVisible = !overlaysVisible
            if (overlaysVisible) scheduleAutoHide(coroutineScope)
        })
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .then(tapModifier)
    ) {
        // PlayerView in background (AndroidView)
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        // Top overlay (translucent)
        if (overlaysVisible) {
            TopAppBar(
                title = { Text(currentFile.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(currentFile.absolutePath) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { onZip(currentFile.absolutePath) }) {
                        Icon(Icons.Default.Archive, contentDescription = "Zip & Share")
                    }
                    IconButton(onClick = {
                        if (isExternal) {
                            Toast.makeText(context, "Cannot delete external file", Toast.LENGTH_LONG).show()
                        } else {
                            // confirm then delete
                            coroutineScope.launch {
                                onDelete(currentFile.absolutePath)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            )
        }

        // Bottom controls overlay
        Column(modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (overlaysVisible) 0.28f else 0.0f))
        ) {
            // playback controls center
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    // prev: play previous clip if available
                    if (currentIndex > 0) {
                        currentIndex--
                        val next = siblings[currentIndex]
                        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(next)))
                        exo.prepare()
                        exo.playWhenReady = true
                        scheduleAutoHide(coroutineScope)
                    }
                }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = {
                    if (exo.isPlaying) exo.pause() else exo.play()
                    isPlaying = exo.isPlaying
                    scheduleAutoHide(coroutineScope)
                }) {
                    Icon(if (exo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(44.dp))
                }

                IconButton(onClick = {
                    // next
                    if (currentIndex < siblings.lastIndex) {
                        currentIndex++
                        val next = siblings[currentIndex]
                        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(next)))
                        exo.prepare()
                        exo.playWhenReady = true
                        scheduleAutoHide(coroutineScope)
                    }
                }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }
            }

            // Seek bar (simple)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // current position / duration text
                val pos = exo.currentPosition
                val dur = if (exo.duration > 0) exo.duration else durationMs
                Text(formatMs(pos), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = (pos.coerceAtLeast(0L).toFloat()),
                    onValueChange = { v ->
                        exo.seekTo(v.toLong())
                    },
                    valueRange = 0f..(dur.coerceAtLeast(0L).toFloat()),
                    modifier = Modifier.weight(1f)
                )
                Text(formatMs(dur), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Thumbnail strip: clickable thumbnails to jump to that video
            LazyRow(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(siblings) { idx, file ->
                    val bmp = thumbs[file.absolutePath]
                    // ensure loaded
                    ensureThumbLocal(file.absolutePath, thumbs, coroutineScope)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier
                            .size(88.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    // jump to this video
                                    currentIndex = idx
                                    exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                                    exo.prepare()
                                    exo.playWhenReady = true
                                    overlaysVisible = true
                                    scheduleAutoHide(coroutineScope)
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = file.name, modifier = Modifier.fillMaxSize())
                            } else {
                                // placeholder: show file icon or progress
                                Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(36.dp))
                            }
                        }
                        Text(text = file.name, maxLines = 1, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(88.dp))
                    }
                }
            }
        }
    }
}

/* ------------------- helpers ------------------- */

private fun isVideoExt(name: String): Boolean {
    val l = name.lowercase()
    return l.endsWith(".mp4") || l.endsWith(".mkv") || l.endsWith(".webm") || l.endsWith(".3gp") || l.endsWith(".mov") || l.endsWith(".avi")
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = TimeUnit.MILLISECONDS.toSeconds(ms)
    val m = s / 60
    val sec = (s % 60).toInt()
    return String.format("%d:%02d", m, sec)
}

/**
 * Ensure thumbnail exists in map; launches coroutine to create if missing.
 * Kept as a separate function to avoid re-capturing Compose state in loops.
 */
private fun ensureThumbLocal(path: String, map: MutableMap<String, Bitmap?>, scope: CoroutineScope) {
    if (map.containsKey(path)) return
    map[path] = null
    scope.launch(Dispatchers.IO) {
        val bmp = generateVideoFrame(path)
        withContext(Dispatchers.Main) { map[path] = bmp }
    }
}

/**
 * Generate a frame bitmap for a video path (MediaMetadataRetriever).
 * Returns null on failure.
 */
private fun generateVideoFrame(path: String): Bitmap? {
    return try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(path)
        // capture frame at 1s or first available
        val timeUs = 1_000_000L
        val frame = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: mmr.frameAtTime
        mmr.release()
        frame
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
