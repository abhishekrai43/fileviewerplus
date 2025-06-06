// File: com/arapps/fileviewplus/ui/screens/VaultScreen.kt

package com.arapps.fileviewplus.ui.screens

import android.widget.Toast
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.vault.*
import com.arapps.fileviewplus.utils.*
import com.arapps.fileviewplus.utils.VaultRestoreManager.restoreVaultFromZip
import com.arapps.fileviewplus.utils.VaultBackupManager.backupVaultToZip
import kotlinx.coroutines.launch
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenFolder: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultRoot = File(context.filesDir, ".vault").apply { mkdirs() }
    var showNotes by remember { mutableStateOf(false) }


    val uploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Toast.makeText(context, "Google Vault backup uploaded!", Toast.LENGTH_LONG).show()
        }
    var showBackupPicker by remember { mutableStateOf(false) }

    var backupMode by remember { mutableStateOf<VaultBackupMode?>(null) }

    var showEnterPin by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showRecoverDialog by remember { mutableStateOf(false) }
    var vaultItems by remember { mutableStateOf(vaultRoot.listFiles()?.toList() ?: emptyList()) }

    val refreshVault: () -> Unit = {
        vaultItems = vaultRoot.listFiles()?.toList() ?: emptyList()
    }

    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hasPin by rememberSaveable { mutableStateOf(getStoredPin(context) != null) }
    val showSetupPin = !hasPin && !unlocked


    val shouldShowVault = hasPin && unlocked

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach { uri -> importFileToVault(context, uri, vaultRoot) }
            refreshVault()
        }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backupMode = VaultBackupMode.RESTORE
            scope.launch {
                val result = restoreVaultFromZip(context, uri, vaultRoot)
                backupMode = null

                when (result) {
                    is VaultRestoreManager.RestoreResult.Success -> {
                        Toast.makeText(context, "Vault restored from backup!", Toast.LENGTH_LONG).show()
                        refreshVault()
                    }
                    is VaultRestoreManager.RestoreResult.Failure -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "No backup selected", Toast.LENGTH_SHORT).show()
        }
    }



    LaunchedEffect(unlocked) {
        if (unlocked) refreshVault()
    }

    Scaffold(
        topBar = {
            if (showNotes) {
                TopAppBar(
                    title = { Text("Notes", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { showNotes = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Vault", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (unlocked) {
                            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Default.Add, contentDescription = "Import Files")
                            }
                            IconButton(onClick = { showCreateFolder = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                            }
                            IconButton(onClick = { showNotes = true }) {
                                Icon(Icons.Default.StickyNote2, contentDescription = "Vault Notes")
                            }
                        }
                    }
                )
            }
        }


    ) { padding ->
        if (showNotes) {
            VaultNotesScreen(onBack = { showNotes = false })
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            when (backupMode) {
                VaultBackupMode.BACKUP -> AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Preparing Backup") },
                    text = { Text("Encrypting vault and preparing upload. Please wait...") }
                )

                VaultBackupMode.RESTORE -> AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Restoring Backup") },
                    text = { Text("Decrypting vault and restoring files. Please wait...") }
                )

                else -> {}
            }


            when {
                showSetupPin -> {
                    SetupPinDialog(
                        onPinSet = { pin ->
                            storePin(context, pin)
                            hasPin = true
                            unlocked = true
                        },
                        onCancel = onBack
                    )

                }

                !unlocked -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        ElevatedButton(
                            onClick = { showEnterPin = true },
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(52.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 6.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Unlock Vault", style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Your files are securely stored here. Unlock to access encrypted content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (vaultItems.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Vault is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Tap below to create a folder or use '+' to import files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                ElevatedButton(
                                    onClick = { showCreateFolder = true },
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Folder")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                ElevatedButton(
                                    onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(64.dp),
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))

                                    Text("Recover from Backup")
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(vaultItems, key = { it.absolutePath }) { file ->
                                    if (file.isDirectory) {
                                        VaultFolderCard(
                                            folder = file,
                                            onOpen = { onOpenFolder(file) },
                                            onRenameRequest = { renameTarget = file },
                                            onDeleteConfirmed = {
                                                file.deleteRecursively()
                                                refreshVault()
                                            },
                                            onZipAndShare = {
                                                val zip = ZipUtils.createZip(
                                                    context,
                                                    file.name,
                                                    file.listFiles()?.map { it.toFileNode() }.orEmpty()
                                                )

                                                zip?.let { ZipUtils.shareZip(context, it) }
                                            }
                                        )
                                    } else {
                                        VaultFileCard(file = file, onFileChanged = refreshVault)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            ElevatedButton(
                                onClick = { showBackupPicker = true }

,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .fillMaxWidth(0.85f)
                                    .height(50.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("1-Click Backup")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            ElevatedButton(
                                onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .fillMaxWidth(0.85f)
                                    .height(50.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Recover from Backup")
                            }
                        }
                    }
                }
            }

            if (showEnterPin) {
                EnterPinDialog(
                    onPinEntered = {
                        if (it == getStoredPin(context)) {
                            unlocked = true
                            showEnterPin = false
                        } else {
                            Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { showEnterPin = false },
                    onForgotPin = {
                        showEnterPin = false
                        showForgotDialog = true
                    }
                )
            }

            if (showForgotDialog) {
                ForgotPinDialog(
                    onRecover = {
                        showForgotDialog = false
                        showRecoverDialog = true
                    },
                    onDismiss = { showForgotDialog = false }
                )
            }

            if (showRecoverDialog) {
                RecoverPinDialog(
                    onRecovered = { showRecoverDialog = false },
                    onDismiss = { showRecoverDialog = false }
                )
            }

            renameTarget?.let { file ->
                ShowRenameDialog(
                    context = context,
                    file = file,
                    onSuccess = {
                        refreshVault()
                        renameTarget = null
                    },
                    onDismiss = { renameTarget = null }
                )
            }
        }

        if (showCreateFolder) {
            CreateFolderDialog(
                onCreate = { folderName ->
                    showCreateFolder = false
                    val success = VaultUtils.createFolderIfNotExists(vaultRoot, folderName)
                    if (success) {
                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                        refreshVault()
                    } else {
                        Toast.makeText(context, "Invalid or existing folder", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showCreateFolder = false }
            )
        }




        if (showEnterPin) {
            EnterPinDialog(
                onPinEntered = { input ->
                    if (input == getStoredPin(context)) {
                        unlocked = true
                        showEnterPin = false
                    } else {
                        Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showEnterPin = false },
                onForgotPin = {
                    showEnterPin = false
                    showForgotDialog = true
                }
            )
        }
            if (showBackupPicker) {
                VaultBackupPickerDialog(
                    context = context,
                    vaultItems = vaultItems,
                    mode = VaultBackupMode.BACKUP,
                    onDismiss = { showBackupPicker = false },
                    onConfirm = { selected ->
                        showBackupPicker = false
                        backupMode = VaultBackupMode.BACKUP
                        backupVaultToZip(
                            context = context,
                            selectedItems = selected,
                            onStarted = { backupMode = VaultBackupMode.BACKUP },
                            onFailed = {
                                backupMode = null
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            },
                            onReadyToShare = { intent ->
                                backupMode = null
                                Toast.makeText(
                                    context,
                                    "Vault backup ready – select Google Drive to upload",
                                    Toast.LENGTH_LONG
                                ).show()
                                uploadLauncher.launch(intent)
                            }
                        )
                    }
                )
            }




        }
}
fun File.toFileNode(): FileNode = FileNode(
    name = name,
    path = absolutePath,
    type = FileNode.FileType.fromExtension(extension),
    size = length(),
    lastModified = lastModified()
    )
}

