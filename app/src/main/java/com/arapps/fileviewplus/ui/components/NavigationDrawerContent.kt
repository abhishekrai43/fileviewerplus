package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationDrawerContent(
    onSafClick: () -> Unit,
    onOpenSafExplorer: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "FileFlow Plus", // updated brand spelling
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        NavigationDrawerItem(
            label = {
                Column {
                    Text("🔐 Full Access Mode")
                    Text(
                        "Rename, move, delete & manage files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            selected = false,
            onClick = onSafClick
        )

    }
}
