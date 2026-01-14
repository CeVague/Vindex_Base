package com.cevague.vindex.data.local

import android.content.Context
import com.cevague.vindex.VindexApplication
import androidx.core.content.edit

object FastSettings {
    private val prefs by lazy {
        VindexApplication.instance.getSharedPreferences("fast_settings", Context.MODE_PRIVATE)
    }

    var isFirstRun: Boolean
        get() = prefs.getBoolean("is_first_run", true)
        set(value) = prefs.edit { putBoolean("is_first_run", value) } // Corrigé

    var sourceFolderUri: String?
        get() = prefs.getString("source_folder_uri", null)
        set(value) = prefs.edit { putString("source_folder_uri", value) }

    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit { putString("theme_mode", value) }

    var lastScanTimestamp: Long
        get() = prefs.getLong("last_scan_timestamp", 0L)
        set(value) = prefs.edit { putLong("last_scan_timestamp", value) }

    var userLanguage: String
        get() = prefs.getString("user_language", "en") ?: "en"
        set(value) = prefs.edit { putString("user_language", value) }
}