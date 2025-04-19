// File: com/arapps/fileviewplus/ui/components/common/DropdownMenuBox.kt
package com.arapps.fileviewplus.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> DropdownMenuBox(
    value: T,
    onValueChange: (T) -> Unit,
    label: String,
    options: List<T>
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = value.toString().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.toString().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onValueChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
