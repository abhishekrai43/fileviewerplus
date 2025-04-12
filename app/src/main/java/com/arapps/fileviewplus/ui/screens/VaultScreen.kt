package com.arapps.fileviewplus.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    context: Context = LocalContext.current,
    onBack: () -> Unit
) {
    var isPinSet by remember { mutableStateOf(false) }
    var showPinEntry by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var storedPin by remember { mutableStateOf<String?>(null) }

    val vaultDir = File(context.filesDir, ".vault").apply { mkdirs() }
    var filesInVault by remember { mutableStateOf(vaultDir.listFiles()?.toList() ?: emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isPinSet) {
                        IconButton(onClick = { showPinEntry = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Change PIN")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isPinSet) {
                FloatingActionButton(onClick = {
                    // Trigger SAF intent to pick file(s) and copy to vaultDir
                    Toast.makeText(context, "Coming soon: Add files to vault", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add File")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (!isPinSet) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Vault is locked. Set a PIN to continue.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                if (filesInVault.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files in vault.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filesInVault) { file ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Optionally open file
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(file.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        file.delete()
                                        filesInVault = vaultDir.listFiles()?.toList() ?: emptyList()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPinEntry) {
            AlertDialog(
                onDismissRequest = { showPinEntry = false },
                title = { Text(if (!isPinSet) "Set Vault PIN" else "Change Vault PIN") },
                text = {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Enter 4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput.length == 4) {
                            storedPin = pinInput
                            isPinSet = true
                            showPinEntry = false
                            Toast.makeText(context, "PIN Set Successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enter a valid 4-digit PIN", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinEntry = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
