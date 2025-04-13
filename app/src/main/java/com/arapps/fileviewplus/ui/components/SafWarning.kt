package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SafWarningIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = "Limited access",
        tint = Color.Red,
        modifier = modifier.size(16.dp)
    )
}
