// File: com/arapps/fileviewplus/ui/screens/VaultNotesScreen.kt

package com.arapps.fileviewplus.ui.screens

import Note
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.ui.components.vault.NoteCard
import com.arapps.fileviewplus.ui.components.vault.NoteDialog
import com.arapps.fileviewplus.utils.NoteBackupManager
import com.arapps.fileviewplus.utils.NoteManager
import com.arapps.fileviewplus.utils.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultNotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(listOf<Note>()) }
    var showDialog by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf<Note?>(null) }

    fun loadNotes() {
        scope.launch(Dispatchers.IO) {
            val loaded = NoteManager.loadNotes(context)
            notes = loaded
        }
    }

    LaunchedEffect(Unit) {
        loadNotes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val zip = NoteBackupManager.backupNotes(context)
                        if (zip != null) {
                            NoteBackupManager.shareBackup(context, zip)
                        } else {
                            Toast.makeText(context, "No notes to backup", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Backup Notes")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editNote = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No notes yet. Tap + to create one.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(note = note, onEdit = {
                            editNote = it
                            showDialog = true
                        }, onDelete = {
                            scope.launch(Dispatchers.IO) {
                                NoteManager.deleteNote(context, it)
                                ReminderScheduler.cancelReminder(context, it.id)
                                loadNotes()
                            }


                    })
                    }
                }
            }
        }

        if (showDialog) {
            NoteDialog(
                initialNote = editNote,
                onDismiss = { showDialog = false },
                onSave = { note ->
                    scope.launch(Dispatchers.IO) {
                        NoteManager.saveOrUpdateNote(context, note)

                        note.reminderAt?.let {
                            ReminderScheduler.scheduleReminder(
                                context = context,
                                noteId = note.id,
                                noteContent = note.content,
                                triggerAtMillis = it,
                                repeat = note.repeat
                            )
                        }

                        loadNotes()
                    }
                    showDialog = false
                }

            )
        }
    }
}
