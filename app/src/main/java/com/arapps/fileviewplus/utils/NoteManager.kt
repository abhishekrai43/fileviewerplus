// File: com/arapps/fileviewplus/utils/NoteManager.kt

package com.arapps.fileviewplus.utils

import Note
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object NoteManager {
    private val gson = Gson()
    private const val FILE_NAME = "notes.json"

    private fun getNotesFile(context: Context): File {
        val dir = File(context.filesDir, "notes").apply { mkdirs() }
        return File(dir, FILE_NAME)
    }

    fun loadNotes(context: Context): List<Note> {
        return try {
            val file = getNotesFile(context)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            gson.fromJson(json, object : TypeToken<List<Note>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveOrUpdateNote(context: Context, note: Note) {
        val existing = loadNotes(context).toMutableList()
        val index = existing.indexOfFirst { it.id == note.id }
        if (index != -1) existing[index] = note else existing.add(note)
        saveAll(context, existing)
    }

    fun deleteNote(context: Context, note: Note) {
        val filtered = loadNotes(context).filterNot { it.id == note.id }
        saveAll(context, filtered)
    }

    private fun saveAll(context: Context, notes: List<Note>) {
        try {
            val file = getNotesFile(context)
            file.writeText(gson.toJson(notes))
        } catch (_: Exception) {}
    }
}
