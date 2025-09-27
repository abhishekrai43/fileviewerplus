package com.arapps.fileviewplus.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun storePin(context: Context, pin: String) {
    // Only set the PIN if one does not already exist — avoid accidental resets from other flows
    val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("vault_pin", null)
    if (existing == null) {
        prefs.edit().putString("vault_pin", pin).apply()
    }
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

/** Copy a normal file (by path) into the vault folder. Returns the destination File or null on failure. */
fun copyFileToVault(srcPath: String, destDir: File): File? {
    return try {
        val src = File(srcPath)
        if (!src.exists()) return null
        val dest = File(destDir, src.name)
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

/** Copy multiple files into the destination vault folder, returns list of successfully copied files. */
suspend fun copyFilesToVaultAsync(context: Context, srcPaths: List<String>, destDir: File): List<File> =
    withContext(Dispatchers.IO) {
        val copied = mutableListOf<File>()
        srcPaths.forEach { p ->
            try {
                val f = copyFileToVault(p, destDir)
                if (f != null) copied.add(f)
            } catch (_: Exception) {}
        }
        copied
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
    // Only set recovery info if a PIN does not already exist (avoid accidental overwrite)
    val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("vault_pin", null)
    if (existing == null) {
        prefs.edit().apply {
            putString("vault_pin", pin)
            putString("vault_hint", hint)
            putString("vault_answer", answer.lowercase().trim())
            apply()
        }
    }
}
