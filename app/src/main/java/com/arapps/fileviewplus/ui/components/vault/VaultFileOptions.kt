package com.arapps.fileviewplus.ui.components.vault

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.utils.ZipUtils
import java.io.File

@Composable
fun VaultFileOptionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onZipShare: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Rename") }, onClick = {
            onDismiss()
            onRename()
        })
        DropdownMenuItem(text = { Text("Delete") }, onClick = {
            onDismiss()
            onDelete()
        })
        DropdownMenuItem(text = { Text("Zip & Share") }, onClick = {
            onDismiss()
            onZipShare()
        })
    }
}
