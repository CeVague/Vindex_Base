package com.cevague.vindex.data.local

import android.content.Context
import androidx.core.content.edit
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Setting

object FastSettings {
    private val prefs by lazy {
        VindexApplication.instance.getSharedPreferences("fast_settings", Context.MODE_PRIVATE)
    }

    var isFirstRun: Boolean
        get() = prefs.getBoolean(Setting.KEY_FIRST_RUN, true)
        set(value) = prefs.edit { putBoolean(Setting.KEY_FIRST_RUN, value) }
    
    var isCitiesLoaded: Boolean
        get() = prefs.getBoolean(Setting.KEY_CITIES_LOADED, false)
        set(value) = prefs.edit { putBoolean(Setting.KEY_CITIES_LOADED, value) }

    var sourceFolderUri: String?
        get() = prefs.getString(Setting.KEY_SOURCE_FOLDER_URI, null)
        set(value) = prefs.edit { putString(Setting.KEY_SOURCE_FOLDER_URI, value) }

    var themeMode: String
        get() = prefs.getString(Setting.KEY_THEME, Setting.THEME_SYSTEM) ?: Setting.THEME_SYSTEM
        set(value) = prefs.edit { putString(Setting.KEY_THEME, value) }

    var lastScanTimestamp: Long
        get() = prefs.getLong(Setting.KEY_LAST_SCAN_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(Setting.KEY_LAST_SCAN_TIMESTAMP, value) }

    var userLanguage: String
        get() = prefs.getString(Setting.KEY_LANGUAGE, Setting.LANGUAGE_SYSTEM)
            ?: Setting.LANGUAGE_SYSTEM
        set(value) = prefs.edit { putString(Setting.KEY_LANGUAGE, value) }
}