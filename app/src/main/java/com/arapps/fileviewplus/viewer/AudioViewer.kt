package com.arapps.fileviewplus.viewer

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import java.io.File

@Composable
fun AudioViewer(
    fileNode: FileNode,
    isVault: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(fileNode.path) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(File(fileNode.path).absolutePath)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                // ready to play
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
            }
        } catch (e: Exception) {
            // ignore errors - playback may not be available on some devices/paths
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
            } catch (_: Exception) {}
            try { mediaPlayer.release() } catch (_: Exception) {}
        }
    }

    Card(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = fileNode.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = fileNode.path, style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    try {
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

                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
        }
    }
}

