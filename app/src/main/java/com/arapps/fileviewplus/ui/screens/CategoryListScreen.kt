package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.items
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.model.FilterMode
import com.arapps.fileviewplus.model.NavigationState
import com.arapps.fileviewplus.ui.components.*
import com.arapps.fileviewplus.utils.FileAnalytics
import com.arapps.fileviewplus.utils.getLocalIpAddress
import com.arapps.fileviewplus.utils.isOnWifi
import com.arapps.ftpserver.FtpServerController
import com.arapps.fileviewplus.server.HttpFileServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@SuppressLint("NewApi", "UnusedBoxWithConstraintsScope")
@Composable
fun CategoryListScreen(
    categories: List<FileNode.Category>,
    onSelect: (FileNode.Category) -> Unit,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onGoHome: () -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onVaultClick: () -> Unit,
    nav: MutableState<NavigationState>,
    onRefresh: () -> Unit = {},
    isScanning: Boolean = false
) {
    val context = LocalContext.current
    val useFtp = remember { mutableStateOf(true) }
    val isServerRunning = remember { mutableStateOf(false) }
    val showDialog = remember { mutableStateOf(false) }
    val ipAddress = remember { mutableStateOf("") }
    val protocol = remember { mutableStateOf("FTP") }

    val stats = remember { mutableStateOf<List<StorageStats.Stat>>(emptyList()) }
    // MediaStore-based stats (more authoritative for system-reported media usage)
    val mediaStats = remember { mutableStateOf<List<StorageStats.Stat>>(emptyList()) }

    val insights = remember { mutableStateOf<List<FileAnalytics.FileInsight>>(emptyList()) }

    // Active inline audio player (shows a compact row when set)
    val activeAudio = remember { mutableStateOf<FileNode?>(null) }

    val allFiles = remember(categories) {
        categories.flatMap { cat ->
            cat.years.flatMap { it.months }
                .flatMap { it.days }
                .flatMap { it.files }
        }
    }

    // Fetch analytics for stats + suggestions
    LaunchedEffect(categories) {
        stats.value = StorageStats.calculateStats(categories)
        withContext(Dispatchers.IO) {
            insights.value = allFiles.map {
                FileAnalytics.FileInsight(
                    file = it,
                    size = it.size,
                    lastModified = it.lastModified
                )
            }
            // Also query MediaStore for totals (permission-aware); prefer these numbers when they are larger
            try {
                val ms = StorageStats.calculateMediaStoreStats(context)
                mediaStats.value = ms
            } catch (_: Exception) {
                // ignore - leave mediaStats empty
            }
        }
    }

    // Generate suggestions
    val oldFiles = remember(insights.value) { FileAnalytics.getOldFiles(insights.value, 180) }
    val largeFiles = remember(insights.value) { FileAnalytics.getLargeFiles(insights.value, 200) }
    val duplicateFiles =
        remember(insights.value) { FileAnalytics.getDuplicateFiles(insights.value) }

    val suggestions = listOfNotNull(
        oldFiles.takeIf { it.isNotEmpty() }?.let { old ->
            "\uD83D\uDCC5 ${old.size} file${if (old.size > 1) "s" else ""} not opened in 6 months" to {
                nav.value = nav.value.copy(
                    showFilteredList = true,
                    filteredFiles = old.map { it.file },
                    filteredTitle = "Old Files",
                    filterMode = FilterMode.OLD
                )
            }
        },
        largeFiles.takeIf { it.isNotEmpty() }?.let { large ->
            "\uD83D\uDC00 ${large.size} large file${if (large.size > 1) "s" else ""} (>200MB)" to {
                nav.value = nav.value.copy(
                    showFilteredList = true,
                    filteredFiles = large.map { it.file },
                    filteredTitle = "Large Files",
                    filterMode = FilterMode.LARGE
                )
            }
        },
        duplicateFiles.takeIf { it.isNotEmpty() }?.let { dup ->
            "\uD83D\uDCDD ${dup.size} duplicate file${if (dup.size > 1) "s" else ""} found" to {
                nav.value = nav.value.copy(
                    showFilteredList = true,
                    filteredFiles = dup.map { it.file },
                    filteredTitle = "Duplicate Files",
                    filterMode = FilterMode.DUPLICATE
                )
            }
        }
    )

    // Suggestions carousel
    var suggestionIndex by remember { mutableStateOf(0) }
    LaunchedEffect(suggestions) {
        while (true) {
            delay(5000)
            if (suggestions.isNotEmpty()) {
                suggestionIndex = (suggestionIndex + 1) % suggestions.size
            }
        }
    }

    // Show filtered list if active in nav
    if (nav.value.showFilteredList || nav.value.viewerFile != null) {
        // Guard call by runtime API level to satisfy lint: the FilteredFileListScreen may use newer APIs.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            FilteredFileListScreen(
                files = nav.value.filteredFiles.map { File(it.path) },
                title = nav.value.filteredTitle,
                filterMode = nav.value.filterMode,
                onBack = {
                    if (nav.value.viewerFile != null) {
                        nav.value = nav.value.copy(viewerFile = null, viewerIsVault = false)
                    } else {
                        nav.value = nav.value.copy(
                            showFilteredList = false,
                            filteredFiles = emptyList(),
                            filteredTitle = "",
                            filterMode = FilterMode.NONE
                        )
                    }
                },
                onOpenViewer = { file ->
                    nav.value = nav.value.copy(
                        viewerFile = FileNode.fromFile(file),
                        viewerIsVault = false,
                        showFilteredList = true
                    )
                }
            )
        } else {
            // Fallback for older devices: open a simple filtered list via same composable if possible,
            // but to avoid API lint we instead set nav to show a simple filtered list (reuse existing flow).
            // Keep behavior: set showFilteredList so UI shows filtered content elsewhere.
            return
        }
        return
    }

    Scaffold(
        topBar = {
            FileViewTopAppBar(
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                onGoHome = onGoHome,
                isScanning = isScanning,
                onRefresh = onRefresh
            )
        },
        bottomBar = {
            BottomBarActions(
                modifier = Modifier.navigationBarsPadding(),
                onSearch = onSearch,
                onToggleView = onToggleView,
                onToggleServer = {
                    if (isServerRunning.value) {
                        HttpFileServer.stop()
                        FtpServerController.stop()
                        isServerRunning.value = false
                        Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
                    } else {
                        if (!isOnWifi(context)) {
                            Toast.makeText(
                                context,
                                "Connect to Wi-Fi to start server",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@BottomBarActions
                        }
                        ipAddress.value = getLocalIpAddress()
                        if (useFtp.value) {
                            FtpServerController.start(context)
                            protocol.value = "FTP"
                        } else {
                            HttpFileServer.start()
                            protocol.value = "HTTP"
                        }
                        isServerRunning.value = true
                        showDialog.value = true
                        Toast.makeText(context, "Server started", Toast.LENGTH_SHORT).show()
                    }
                },
                isServerRunning = isServerRunning.value,
                serverTypeToggle = {
                    Switch(
                        checked = useFtp.value,
                        onCheckedChange = { useFtp.value = it },
                        modifier = Modifier.size(ButtonDefaults.MinHeight)
                    )
                },
                onShareApp = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Try FileFlow Plus")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out this awesome file manager:\nhttps://play.google.com/store/apps/details?id=com.arapps.fileviewplus"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                },
                onVaultClick = onVaultClick
            )
        }
    ) { innerPadding ->
        // Wrap everything in a Column that respects Scaffold padding so the bottomBar is accounted for.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Define the summary keys/colors/extension fallbacks used by the cards and filtering
            val keyToDisplay = listOf(
                Triple("IMG", FileCategory.IMAGE, "Images"),
                Triple("VID", FileCategory.VIDEO, "Videos"),
                Triple("AUDIO", FileCategory.AUDIO, "Audio"),
                Triple("DOC", FileCategory.DOCUMENT, "Documents"),
                // Add Others (OTH) to surface any file types the scanner categorizes as OTH
                Triple("OTH", FileCategory.OTHER, "Others")
            )

            val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr", "opus", "wma")

            val categoryColorMap = mapOf(
                "DOC" to Color(0xFF6A1B9A),
                "IMG" to Color(0xFF0288D1),
                "VID" to Color(0xFF2E7D32),
                "AUDIO" to Color(0xFFEF6C00)
                // OTH color (teal-ish)
            ).toMutableMap().apply { put("OTH", Color(0xFF00838F)) }

            // Compute counts and sizes from the flattened allFiles list so AUDIO is detected even when
            // there is no dedicated "AUDIO" category. This is robust across different category naming.
            // Robust file-type predicates: prefer FileType but fall back to extension/name matching
            fun isAudioFile(fn: FileNode): Boolean {
                try {
                    val ext =
                        if (fn.extension.isNotBlank()) fn.extension.trim() else fn.name.substringAfterLast(
                            '.',
                            ""
                        ).lowercase().trim()
                    val path = fn.path.lowercase()
                    if (fn.type == FileNode.FileType.Audio) return true
                    if (ext.isNotEmpty() && audioExts.contains(ext)) return true
                    // sometimes names contain extension-like suffixes, check path ending
                    if (audioExts.any { path.endsWith("." + it) }) return true
                } catch (_: Exception) {
                }
                return false
            }

            fun isImageFile(fn: FileNode): Boolean {
                return fn.type == FileNode.FileType.Image || fn.extension in setOf(
                    "jpg",
                    "jpeg",
                    "png",
                    "webp",
                    "gif",
                    "heic"
                )
            }

            fun isVideoFile(fn: FileNode): Boolean {
                return fn.type == FileNode.FileType.Video || fn.extension in setOf(
                    "mp4",
                    "mkv",
                    "mov",
                    "avi",
                    "wmv",
                    "webm",
                    "3gp"
                )
            }

            fun isDocumentFile(fn: FileNode): Boolean {
                // Only treat PDF, DOC, DOCX and TXT as documents for the Documents card.
                // Everything else (csv, json, xml, xls, apk, zip, etc.) should fall into Others.
                val ext = fn.extension.lowercase()
                return ext in setOf("pdf", "doc", "docx", "txt")
            }

            // derive counts and bytes from the live insights list so deletions update counts immediately
            val countsByKey = remember(insights.value) {
                mapOf(
                    "IMG" to insights.value.count { isImageFile(it.file) },
                    "VID" to insights.value.count { isVideoFile(it.file) },
                    "AUDIO" to insights.value.count { isAudioFile(it.file) },
                    "DOC" to insights.value.count { isDocumentFile(it.file) },
                    // OTH: files not matching image/video/audio/document
                    "OTH" to insights.value.count {
                        !isImageFile(it.file) && !isVideoFile(it.file) && !isDocumentFile(
                            it.file
                        ) && !isAudioFile(it.file)
                    }
                )
            }

            val bytesByKey = remember(insights.value) {
                mapOf(
                    "IMG" to insights.value.filter { isImageFile(it.file) }.sumOf { it.size },
                    "VID" to insights.value.filter { isVideoFile(it.file) }.sumOf { it.size },
                    "AUDIO" to insights.value.filter { isAudioFile(it.file) }.sumOf { it.size },
                    "DOC" to insights.value.filter { isDocumentFile(it.file) }.sumOf { it.size },
                    "OTH" to insights.value.filter {
                        !isImageFile(it.file) && !isVideoFile(it.file) && !isDocumentFile(
                            it.file
                        ) && !isAudioFile(it.file)
                    }.sumOf { it.size }
                )
            }

            // Helper functions (declared plainly at this scope) to merge scanner and MediaStore data
            fun findMediaTotalFor(key: String): Long {
                val synonyms = when (key.uppercase()) {
                    "IMG" -> listOf("img", "image", "images")
                    "VID" -> listOf("vid", "video", "videos")
                    "AUDIO" -> listOf("audio", "audios")
                    "DOC" -> listOf("doc", "document", "documents")
                    else -> listOf(key.lowercase())
                }
                return mediaStats.value.firstOrNull { ms ->
                    val name = ms.name
                    synonyms.any { syn ->
                        name.equals(syn, ignoreCase = true) || name.contains(
                            syn,
                            ignoreCase = true
                        )
                    }
                }?.totalBytes ?: 0L
            }

            fun findMediaCountFor(key: String): Int {
                val synonyms = when (key.uppercase()) {
                    "IMG" -> listOf("img", "image", "images")
                    "VID" -> listOf("vid", "video", "videos")
                    "AUDIO" -> listOf("audio", "audios")
                    "DOC" -> listOf("doc", "document", "documents")
                    else -> listOf(key.lowercase())
                }
                return mediaStats.value.firstOrNull { ms ->
                    val name = ms.name
                    synonyms.any { syn ->
                        name.equals(syn, ignoreCase = true) || name.contains(
                            syn,
                            ignoreCase = true
                        )
                    }
                }?.count ?: 0
            }

            val displayStats = remember(bytesByKey, mediaStats.value) {
                listOf(
                    StorageStats.Stat(
                        "DOC",
                        if ((bytesByKey["DOC"]
                                ?: 0L) == 0L
                        ) findMediaTotalFor("DOC") else (bytesByKey["DOC"] ?: 0L),
                        if ((countsByKey["DOC"]
                                ?: 0) == 0
                        ) findMediaCountFor("DOC") else (countsByKey["DOC"] ?: 0)
                    ),
                    StorageStats.Stat(
                        "IMG",
                        if ((bytesByKey["IMG"]
                                ?: 0L) == 0L
                        ) findMediaTotalFor("IMG") else (bytesByKey["IMG"] ?: 0L),
                        if ((countsByKey["IMG"]
                                ?: 0) == 0
                        ) findMediaCountFor("IMG") else (countsByKey["IMG"] ?: 0)
                    ),
                    StorageStats.Stat(
                        "VID",
                        if ((bytesByKey["VID"]
                                ?: 0L) == 0L
                        ) findMediaTotalFor("VID") else (bytesByKey["VID"] ?: 0L),
                        if ((countsByKey["VID"]
                                ?: 0) == 0
                        ) findMediaCountFor("VID") else (countsByKey["VID"] ?: 0)
                    ),
                    StorageStats.Stat(
                        "AUDIO",
                        if ((bytesByKey["AUDIO"]
                                ?: 0L) == 0L
                        ) findMediaTotalFor("AUDIO") else (bytesByKey["AUDIO"] ?: 0L),
                        if ((countsByKey["AUDIO"]
                                ?: 0) == 0
                        ) findMediaCountFor("AUDIO") else (countsByKey["AUDIO"] ?: 0)
                    ),
                    StorageStats.Stat(
                        "OTH",
                        if ((bytesByKey["OTH"]
                                ?: 0L) == 0L
                        ) findMediaTotalFor("OTH") else (bytesByKey["OTH"] ?: 0L),
                        if ((countsByKey["OTH"]
                                ?: 0) == 0
                        ) findMediaCountFor("OTH") else (countsByKey["OTH"] ?: 0)
                    )
                )
            }

            // Compute display counts preferring MediaStore counts when scanner counts are zero
            val displayCounts = remember(countsByKey, mediaStats.value) {
                fun mediaCountFor(key: String): Int {
                    val synonyms = when (key.uppercase()) {
                        "IMG" -> listOf("img", "image", "images")
                        "VID" -> listOf("vid", "video", "videos")
                        "AUDIO" -> listOf("audio", "audios")
                        "DOC" -> listOf("doc", "document", "documents")
                        else -> listOf(key.lowercase())
                    }
                    return mediaStats.value.firstOrNull { ms ->
                        val name = ms.name
                        synonyms.any { syn ->
                            name.equals(syn, ignoreCase = true) || name.contains(
                                syn,
                                ignoreCase = true
                            )
                        }
                    }?.count ?: 0
                }

                mapOf(
                    "IMG" to (countsByKey["IMG"]?.takeIf { it != 0 } ?: mediaCountFor("IMG")),
                    "VID" to (countsByKey["VID"]?.takeIf { it != 0 } ?: mediaCountFor("VID")),
                    "AUDIO" to (countsByKey["AUDIO"]?.takeIf { it != 0 } ?: mediaCountFor("AUDIO")),
                    "DOC" to (countsByKey["DOC"]?.takeIf { it != 0 } ?: mediaCountFor("DOC")),
                    "OTH" to (countsByKey["OTH"]?.takeIf { it != 0 } ?: mediaCountFor("OTH"))
                )
            }

            // Replaced the previous StorageUsageBar Column with a compact 2-column chips grid
            // so the storage stats occupy less vertical space while remaining visible.
            val totalBytesForPct = displayStats.sumOf { it.totalBytes }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayStats) { stat ->
                    val pct = if (totalBytesForPct > 0L) (stat.totalBytes * 100.0 / totalBytesForPct).toInt() else 0
                    Card(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(categoryColorMap[stat.name] ?: MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (stat.name == "OTH") "Others - ${StorageStats.formatSize(stat.totalBytes)} ($pct%)" else "${stat.name} - ${StorageStats.formatSize(stat.totalBytes)} ($pct%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                val (text, action) = suggestions[suggestionIndex]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(onClick = action) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Main cards grid (Images, Videos, Audio, Documents, Others, Reminder Note)
            val summaryKeys = listOf("IMG", "VID", "AUDIO", "DOC", "OTH", "REMINDER")
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(summaryKeys) { key ->
                    if (key == "REMINDER") {
                        Card(
                            modifier = Modifier
                                .height(96.dp)
                                .fillMaxWidth()
                                .clickable {
                                    nav.value = nav.value.copy(
                                        showVault = true,
                                        showVaultNotes = true,
                                        showVaultNotesCreation = true
                                    )
                                },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.StickyNote2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Reminders, Notes, Save Passwords",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Create Notes, Reminders etc",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    } else {
                        val label = keyToDisplay.find { it.first == key }?.third ?: key
                        val count = displayCounts[key] ?: (countsByKey[key] ?: 0)
                        val sizeBytes = displayStats.find { it.name.equals(key, ignoreCase = true) }?.totalBytes ?: (bytesByKey[key] ?: 0L)

                        Card(
                            modifier = Modifier
                                .height(96.dp)
                                .fillMaxWidth()
                                .clickable {
                                    if (key == "OTH") {
                                        // Show all files NOT in IMG, VID, AUDIO, DOC as a flat list
                                        val filesForOthers = allFiles.filter {
                                            !isImageFile(it) && !isVideoFile(it) && !isAudioFile(it) && !isDocumentFile(it)
                                        }
                                        if (filesForOthers.isNotEmpty()) {
                                            nav.value = nav.value.copy(
                                                showFilteredList = true,
                                                filteredFiles = filesForOthers,
                                                filteredTitle = label,
                                                filterMode = FilterMode.OTHERS
                                            )
                                        }
                                    } else if (key == "IMG" || key == "VID" || key == "AUDIO" || key == "DOC") {
                                        // Always navigate to Year/Month/Day view for main categories
                                        val cat = categories.find { it.name.equals(key, ignoreCase = true) }
                                            ?: if (key == "AUDIO") categories.find { it.name.equals("HIDDEN_AUDIO", ignoreCase = true) } else null
                                        if (cat != null) onSelect(cat)
                                    } else {
                                        // No action for unknown keys
                                    }
                                },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(categoryColorMap[key] ?: MaterialTheme.colorScheme.primary))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("$count", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(StorageStats.formatSize(sizeBytes), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Show server started dialog outside scaffold content so it overlays correctly
            if (showDialog.value) {
                val port = if (protocol.value == "FTP") 2121 else 8080
                AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = { Text("Server Started ✅") },
                    text = {
                        Text(
                            "Protocol: ${protocol.value}\n" +
                                    "IP Address: ${ipAddress.value}\n" +
                                    "Port: $port\n\n" +
                                    "No username/password needed.\n" +
                                    "If using FTP, use a client like FileZilla.\n\n" +
                                    "For HTTP, open ${ipAddress.value}:$port in your browser."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showDialog.value = false }) { Text("OK") }
                    }
                )
            }
        }
    }
}