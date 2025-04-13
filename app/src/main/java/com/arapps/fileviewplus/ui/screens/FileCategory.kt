package com.arapps.fileviewplus.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector

enum class FileCategory(val label: String, val icon: ImageVector) {
    IMAGE("Images", Icons.Default.Image),
    VIDEO("Videos", Icons.Default.Movie),
    AUDIO("Audio", Icons.Default.MusicNote),
    DOCUMENT("Documents", Icons.Default.Description),
    OTHER("Others", Icons.Default.Description)
}
