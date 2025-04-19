package com.arapps.fileviewplus.data

import Note
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object NoteStore {
    private const val NOTES_FILE = "vault_notes.json"

    fun getAllNotes(context: Context): List<Note> {
        val file = File(context.filesDir, NOTES_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val type = object : TypeToken<List<Note>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAllNotes(context: Context, notes: List<Note>) {
        val file = File(context.filesDir, NOTES_FILE)
        val json = Gson().toJson(notes)
        file.writeText(json)
    }
}
