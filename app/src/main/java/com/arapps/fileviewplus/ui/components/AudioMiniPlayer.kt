package com.arapps.fileviewplus.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AudioMiniPlayer(
    fileNode: FileNode,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    overlay: Boolean = false,
    onClose: () -> Unit = {}
) {
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var seekByUser by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(0f) }

    // MediaPlayer remembered so it survives recompositions for same file
    val mediaPlayer = remember(fileNode.path) {
        MediaPlayer()
    }

    DisposableEffect(fileNode.path) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(File(fileNode.path).absolutePath)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                durationMs = mp.duration
                isPrepared = true
                if (autoPlay) {
                    try {
                        mediaPlayer.start()
                        isPlaying = true
                    } catch (_: Exception) {}
                }
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
        } catch (e: Exception) {
            // ignore; file might be inaccessible on some devices
            isPrepared = false
            isPlaying = false
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
            } catch (_: Exception) {}
            try { mediaPlayer.release() } catch (_: Exception) {}
        }
    }

    // Progress updater when playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                positionMs = mediaPlayer.currentPosition
                if (!seekByUser && durationMs > 0) {
                    sliderPosition = positionMs.toFloat() / durationMs.toFloat()
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }

    val playerCard = @Composable {
        Card(modifier = modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Play / Pause button
                    Button(onClick = {
                        try {
                            if (!isPrepared) return@Button
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        } catch (_: Exception) { }
                    }) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileNode.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Slider + time row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // elapsed time
                            Text(formatMs(positionMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = sliderPosition.coerceIn(0f, 1f),
                                onValueChange = { v ->
                                    sliderPosition = v
                                    seekByUser = true
                                    positionMs = (v * durationMs).toInt()
                                },
                                onValueChangeFinished = {
                                    try {
                                        val target = (sliderPosition * durationMs).toInt()
                                        mediaPlayer.seekTo(target)
                                    } catch (_: Exception) {}
                                    seekByUser = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // total duration
                            Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextButton(onClick = {
                        try {
                            if (mediaPlayer.isPlaying) mediaPlayer.stop()
                        } catch (_: Exception) {}
                        onClose()
                    }) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (overlay) {
        // full-screen dimmed overlay with centered card; clicking outside closes
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onClose() }
        ) {
            Box(modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
            ) {
                // stop clicks from propagating to background
                Box(modifier = Modifier.clickable(enabled = true, onClick = { /* consume */ })) {
                    playerCard()
                }
            }
        }
        return
    } else {
        playerCard()
        return
    }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong())
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
