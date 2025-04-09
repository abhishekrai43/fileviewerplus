package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationDrawerContent(
    onSafClick: () -> Unit,
    onShareClick: () -> Unit,
    onServerClick: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "FileView Plus",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("🔐 SAF File Access") },
            selected = false,
            onClick = onSafClick
        )

        NavigationDrawerItem(
            label = { Text("📡 Share App") },
            selected = false,
            onClick = onShareClick
        )

        NavigationDrawerItem(
            label = { Text("🌐 Start Server") },
            selected = false,
            onClick = onServerClick
        )
    }
}
