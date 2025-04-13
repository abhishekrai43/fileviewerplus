// File: app/src/main/java/com/arapps/fileviewplus/settings/DataStoreExtensions.kt
package com.arapps.fileviewplus.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "app_settings")
