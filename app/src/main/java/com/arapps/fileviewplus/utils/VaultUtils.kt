package com.arapps.fileviewplus.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

fun storePin(context: Context, pin: String) {
    context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        .edit().putString("vault_pin", pin).apply()
}

fun getStoredPin(context: Context): String? {
    return try {
        context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            .getString("vault_pin", null)
    } catch (e: Exception) {
        null // Fail-safe in case of corrupted prefs
    }
}

@SuppressLint("Range")
fun importFileToVault(context: Context, uri: Uri, destDir: File): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    val name = cursor?.use {
        if (it.moveToFirst()) it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) else "unknown"
    } ?: "imported_file"
    val destFile = File(destDir, name)

    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(destFile).use { output ->
            input.copyTo(output)
        }
    }
    return name
}

object VaultUtils {
    fun createFolderIfNotExists(parentDir: File, name: String): Boolean {
        val sanitized = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
        if (sanitized.isEmpty()) return false
        val folder = File(parentDir, sanitized)
        return if (!folder.exists()) folder.mkdirs() else false
    }
}

fun storeRecoveryInfo(context: Context, question: String, answer: String) {
    context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE).edit().apply {
        putString("vault_hint", question.trim())
        putString("vault_answer", answer.trim())
    }.apply()
}

fun getRecoveryHint(context: Context): String? {
    return context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        .getString("vault_hint", null)
}

fun verifyRecoveryAnswer(context: Context, input: String): Boolean {
    val stored = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        .getString("vault_answer", null)
    return input.trim() == stored
}
fun storePinRecovery(context: Context, pin: String, hint: String, answer: String) {
    context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE).edit().apply {
        putString("vault_pin", pin)
        putString("vault_hint", hint)
        putString("vault_answer", answer.lowercase().trim())
        apply()
    }
}
