package com.arapps.fileviewplus.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

object ThemeSettings {
    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

    fun getThemeFlow(context: Context) = context.themeDataStore.data
        .map { prefs -> prefs[DARK_MODE_KEY] ?: false }

    suspend fun setDarkMode(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }
}
