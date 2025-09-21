package com.arapps.fileviewplus.viewer

import androidx.compose.runtime.mutableStateOf
import com.arapps.fileviewplus.model.FileNode

object PlaybackController {
    // Holds the currently-active file to play inline; observed from Compose root
    val active = mutableStateOf<FileNode?>(null)

    fun play(file: FileNode) {
        active.value = file
    }

    fun stop() {
        active.value = null
    }
}

