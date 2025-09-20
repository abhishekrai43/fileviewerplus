package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
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
import java.text.SimpleDateFormat
import java.util.*

// Helper to convert month names ("Jan", "January", or numeric strings) to month numbers for sorting
private fun monthNameToNumber(monthName: String): Int {
    val s = monthName.trim()
    // Try numeric input first
    s.toIntOrNull()?.let { if (it in 1..12) return it }
    if (s.isEmpty()) return 0
    val key = s.take(3).lowercase(Locale.getDefault())
    return when (key) {
        "jan" -> 1
        "feb" -> 2
        "mar" -> 3
        "apr" -> 4
        "may" -> 5
        "jun" -> 6
        "jul" -> 7
        "aug" -> 8
        "sep" -> 9
        "oct" -> 10
        "nov" -> 11
        "dec" -> 12
        else -> 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    nav: MutableState<NavigationState>
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

    val suggestions = listOfNotNull(
        oldFiles.takeIf { it.isNotEmpty() }?.let { old ->
            "\uD83D\uDCC5 ${old.size} file${if (old.size > 1) "s" else ""} not opened in 6 months" to {
                nav.value = nav.value.copy(
                    showFilteredList = true,
                    filteredFiles = old.map { it.file },
                    filteredTitle = "Old Files"
                )
            }
        },
        largeFiles.takeIf { it.isNotEmpty() }?.let { large ->
            "\uD83D\uDC00 ${large.size} large file${if (large.size > 1) "s" else ""} (>200MB)" to {
                nav.value = nav.value.copy(
                    showFilteredList = true,
                    filteredFiles = large.map { it.file },
                    filteredTitle = "Large Files"
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
                onGoHome = onGoHome
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
                            Toast.makeText(context, "Connect to Wi-Fi to start server", Toast.LENGTH_SHORT).show()
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
                        putExtra(Intent.EXTRA_TEXT, "Check out this awesome file manager:\nhttps://play.google.com/store/apps/details?id=com.arapps.fileviewplus")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                },
                onVaultClick = onVaultClick
            )
        }
    ) { innerPadding ->
        // Wrap everything in a Column that respects Scaffold padding so the bottomBar is accounted for.
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Define the summary keys/colors/extension fallbacks used by the cards and filtering
            val keyToDisplay = listOf(
                Triple("IMG", FileCategory.IMAGE, "Images"),
                Triple("VID", FileCategory.VIDEO, "Videos"),
                Triple("AUDIO", FileCategory.AUDIO, "Audio"),
                Triple("DOC", FileCategory.DOCUMENT, "Documents")
            )

            val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr", "opus", "wma")

            val categoryColorMap = mapOf(
                "DOC" to Color(0xFF6A1B9A),
                "IMG" to Color(0xFF0288D1),
                "VID" to Color(0xFF2E7D32),
                "AUDIO" to Color(0xFFEF6C00)
            )

            // Compute counts and sizes from the flattened allFiles list so AUDIO is detected even when
            // there is no dedicated "AUDIO" category. This is robust across different category naming.
            // Robust file-type predicates: prefer FileType but fall back to extension/name matching
            fun isAudioFile(fn: FileNode): Boolean {
                try {
                    val ext = if (fn.extension.isNotBlank()) fn.extension.trim() else fn.name.substringAfterLast('.', "").lowercase().trim()
                    val path = fn.path.lowercase()
                    if (fn.type == FileNode.FileType.Audio) return true
                    if (ext.isNotEmpty() && audioExts.contains(ext)) return true
                    // sometimes names contain extension-like suffixes, check path ending
                    if (audioExts.any { path.endsWith("." + it) }) return true
                } catch (_: Exception) { }
                return false
            }

            fun isImageFile(fn: FileNode): Boolean {
                return fn.type == FileNode.FileType.Image || fn.extension in setOf("jpg","jpeg","png","webp","gif","heic")
            }

            fun isVideoFile(fn: FileNode): Boolean {
                return fn.type == FileNode.FileType.Video || fn.extension in setOf("mp4","mkv","mov","avi","wmv","webm","3gp")
            }

            fun isDocumentFile(fn: FileNode): Boolean {
                return fn.type == FileNode.FileType.Document || fn.extension in setOf("pdf","doc","docx","ppt","pptx","xls","xlsx","txt","rtf")
            }

            val countsByKey = remember(allFiles) {
                mapOf(
                    "IMG" to allFiles.count { isImageFile(it) },
                    "VID" to allFiles.count { isVideoFile(it) },
                    "AUDIO" to allFiles.count { isAudioFile(it) },
                    "DOC" to allFiles.count { isDocumentFile(it) }
                )
            }

            val bytesByKey = remember(allFiles) {
                mapOf(
                    "IMG" to allFiles.filter { isImageFile(it) }.sumOf { it.size },
                    "VID" to allFiles.filter { isVideoFile(it) }.sumOf { it.size },
                    "AUDIO" to allFiles.filter { isAudioFile(it) }.sumOf { it.size },
                    "DOC" to allFiles.filter { isDocumentFile(it) }.sumOf { it.size }
                )
            }

            // Build a stats list for the StorageUsageBar that prefers MediaStore numbers when available
            val displayStats = remember(bytesByKey, mediaStats.value) {
                // helper to pick MediaStore when scanner has zero bytes; otherwise prefer scanner totals
                fun mergedBytes(key: String): Long {
                    val scanner = bytesByKey[key] ?: 0L
                    val synonyms = when (key.uppercase()) {
                        "IMG" -> listOf("img", "image", "images")
                        "VID" -> listOf("vid", "video", "videos")
                        "AUDIO" -> listOf("audio", "audios")
                        "DOC" -> listOf("doc", "document", "documents")
                        else -> listOf(key.lowercase())
                    }
                    val media = mediaStats.value.firstOrNull { ms ->
                        val name = ms.name
                        synonyms.any { syn -> name.equals(syn, ignoreCase = true) || name.contains(syn, ignoreCase = true) }
                    }?.totalBytes ?: 0L

                    return if (scanner == 0L) media else scanner
                }

                // choose counts similarly: prefer scanner count unless zero, then use media count
                fun mergedCount(key: String): Int {
                    val scannerCount = countsByKey[key] ?: 0
                    val synonyms = when (key.uppercase()) {
                        "IMG" -> listOf("img", "image", "images")
                        "VID" -> listOf("vid", "video", "videos")
                        "AUDIO" -> listOf("audio", "audios")
                        "DOC" -> listOf("doc", "document", "documents")
                        else -> listOf(key.lowercase())
                    }
                    val mediaCount = mediaStats.value.firstOrNull { ms ->
                        val name = ms.name
                        synonyms.any { syn -> name.equals(syn, ignoreCase = true) || name.contains(syn, ignoreCase = true) }
                    }?.count ?: 0

                    return if (scannerCount == 0) mediaCount else scannerCount
                }

                listOf(
                    StorageStats.Stat("DOC", mergedBytes("DOC"), mergedCount("DOC")),
                    StorageStats.Stat("IMG", mergedBytes("IMG"), mergedCount("IMG")),
                    StorageStats.Stat("VID", mergedBytes("VID"), mergedCount("VID")),
                    StorageStats.Stat("AUDIO", mergedBytes("AUDIO"), mergedCount("AUDIO"))
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
                        val name = ms.name ?: ""
                        synonyms.any { syn -> name.equals(syn, ignoreCase = true) || name.contains(syn, ignoreCase = true) }
                    }?.count ?: 0
                }

                mapOf(
                    "IMG" to (countsByKey["IMG"]?.takeIf { it != 0 } ?: mediaCountFor("IMG")),
                    "VID" to (countsByKey["VID"]?.takeIf { it != 0 } ?: mediaCountFor("VID")),
                    "AUDIO" to (countsByKey["AUDIO"]?.takeIf { it != 0 } ?: mediaCountFor("AUDIO")),
                    "DOC" to (countsByKey["DOC"]?.takeIf { it != 0 } ?: mediaCountFor("DOC"))
                )
            }

            // Render storage usage bar and suggestion card(s) above the grid with deterministic heights
            Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                StorageUsageBar(stats = displayStats)
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

            // Let the grid occupy a compact fixed height so both rows reliably display across devices.
            val fixedGridHeight = 200.dp
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(fixedGridHeight)) {
                 val chunks = keyToDisplay.chunked(2)
                 // Make the column fill the Box height and split rows evenly
                 Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunks.forEach { rowItems ->
                        // Give each row equal vertical weight so the two rows split the available grid height.
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                             rowItems.forEach { (key, _, label) ->
                                val cat = categories.find { it.name == key }
                                val count = displayCounts[key] ?: (countsByKey[key] ?: 0)
                                val sizeBytes = displayStats.find { it.name.equals(key, ignoreCase = true) }?.totalBytes ?: (bytesByKey[key] ?: 0L)

                                // Each card fills half the vertical space of the grid (two rows), minus spacing.
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            if (cat != null) onSelect(cat) else {
                                                val filesForKey = when (key) {
                                                    "IMG" -> allFiles.filter { it.type == FileNode.FileType.Image }
                                                    "VID" -> allFiles.filter { it.type == FileNode.FileType.Video }
                                                    "AUDIO" -> allFiles.filter { it.type == FileNode.FileType.Audio || it.extension in audioExts }
                                                    "DOC" -> allFiles.filter { it.type == FileNode.FileType.Document }
                                                    else -> emptyList()
                                                }
                                                if (filesForKey.isNotEmpty()) {
                                                    if (key == "AUDIO") {
                                                        val firstFile = filesForKey.firstOrNull()
                                                        if (firstFile != null) {
                                                            // Use the existing FileNode so in-app AudioViewer triggers correctly; ensure filtered list is cleared
                                                            nav.value = nav.value.copy(
                                                                viewerFile = firstFile,
                                                                viewerIsVault = false,
                                                                showFilteredList = false,
                                                                filteredFiles = emptyList()
                                                            )
                                                        } else {
                                                            // build virtual category fallback
                                                            val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
                                                            val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())
                                                            val sdfDay = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())

                                                            val yearsMap = mutableMapOf<String, MutableMap<String, MutableMap<String, MutableList<FileNode>>>>()
                                                            filesForKey.forEach { f ->
                                                                val d = Date(f.lastModified)
                                                                val y = sdfYear.format(d)
                                                                val m = sdfMonth.format(d)
                                                                val day = sdfDay.format(d)
                                                                val yearMap = yearsMap.getOrPut(y) { mutableMapOf() }
                                                                val monthMap = yearMap.getOrPut(m) { mutableMapOf() }
                                                                val dayMap = monthMap.getOrPut(day) { mutableListOf() }
                                                                dayMap.add(f)
                                                            }

                                                            val virtualCategory = FileNode.Category(
                                                                name = "AUDIO",
                                                                years = yearsMap.map { (yName, months) ->
                                                                    FileNode.Year(
                                                                        name = yName,
                                                                        months = months.map { (mName, days) ->
                                                                            FileNode.Month(
                                                                                name = mName,
                                                                                days = days.map { (dayName, files) ->
                                                                                    FileNode.Day(name = dayName, files = files.sortedByDescending { it.name })
                                                                                }.sortedByDescending { it.name }
                                                                            )
                                                                        }.sortedByDescending { monthNameToNumber(it.name) }
                                                                    )
                                                                }.sortedByDescending { it.name.toIntOrNull() ?: 0 }
                                                            )
                                                            nav.value = nav.value.copy(category = virtualCategory)
                                                        }
                                                    } else {
                                                        nav.value = nav.value.copy(
                                                            showFilteredList = true,
                                                            filteredFiles = filesForKey.map { FileNode.fromFile(File(it.path)) }.map { it },
                                                            filteredTitle = label
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(categoryColorMap[key] ?: MaterialTheme.colorScheme.primary))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Text(text = "$count", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                                        Text(text = StorageStats.formatSize(sizeBytes), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Compute which categories to show in the scrollable list below the grid
            val statNames = stats.value.map { it.name.trim().lowercase() }
            val labelWords = keyToDisplay.flatMap { listOf(it.first.lowercase(), it.third.trim().lowercase()) }
            val extra = listOf("img", "image", "images", "vid", "video", "videos", "audio", "doc", "document", "documents")
            val excludeSet = (statNames + labelWords + extra).toSet()
            val filtered = categories.filter { cat ->
                val lower = cat.name.trim().lowercase()
                excludeSet.none { ex -> lower == ex || lower.contains(ex) }
            }

            // Category list below the grid — wrap its content and allow scrolling if long.
            LazyColumn(modifier = Modifier.fillMaxWidth().wrapContentHeight(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(filtered) { cat ->
                    val folderFiles = cat.years.flatMap { it.months }.flatMap { it.days }.flatMap { it.files }

                    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onSelect(cat) }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(cat.name, modifier = Modifier.weight(1f))
                            FolderActionsMenu(folderName = cat.name, files = folderFiles)
                        }
                    }
                }
            }
            // end root Column
        }

    }

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
                TextButton(onClick = { showDialog.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}
