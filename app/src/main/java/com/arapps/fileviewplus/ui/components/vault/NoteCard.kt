
package com.arapps.fileviewplus.ui.components.vault

import Note
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arapps.fileviewplus.ui.theme.NotesFont
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteCard(
    note: Note,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    val background = when (note.color) {
        NoteColor.YELLOW -> Color(0xFFF6E122) // softer yellow
        NoteColor.BLUE -> Color(0xFFBBDEFB)
        NoteColor.GREEN -> Color(0xFFC8E6C9)
        NoteColor.RED -> Color(0xFFFFCDD2)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(note) },
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (note.isPassword) "••••••••••••" else note.content,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                fontFamily = NotesFont,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                note.reminderAt?.let {
                    val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    Text(
                        text = "⏰ ${fmt.format(Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = NotesFont,
                        color = Color.DarkGray
                    )
                }

                IconButton(onClick = { onDelete(note) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
