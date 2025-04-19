package com.arapps.fileviewplus.utils

import android.content.Context

data class RecoveryData(val hint: String, val answer: String, val pin: String)

fun storeRecovery(context: Context, hint: String, answer: String) {
    val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("recovery_hint", hint)
        .putString("recovery_answer", answer)
        .apply()
}

fun getStoredRecovery(context: Context): RecoveryData? {
    val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    val hint = prefs.getString("vault_hint", null)
    val answer = prefs.getString("vault_answer", null)
    val pin = prefs.getString("vault_pin", null)

    return if (!hint.isNullOrBlank() && !answer.isNullOrBlank() && !pin.isNullOrBlank()) {
        RecoveryData(hint, answer, pin)
    } else null
}
