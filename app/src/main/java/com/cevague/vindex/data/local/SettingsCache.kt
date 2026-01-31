package com.cevague.vindex.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.cevague.vindex.data.database.entity.Setting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsCache @Inject constructor(
    private val prefs: SharedPreferences
) {
    // ════════════════════════════════════════════════════════════════════════
    // Configuration de l'application
    // ════════════════════════════════════════════════════════════════════════

    var isFirstRun: Boolean
        get() = prefs.getBoolean(Setting.KEY_FIRST_RUN, true)
        set(value) = prefs.edit { putBoolean(Setting.KEY_FIRST_RUN, value) }

    var isConfigured: Boolean
        get() = prefs.getBoolean(Setting.KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit { putBoolean(Setting.KEY_IS_CONFIGURED, value) }

    var isCitiesLoaded: Boolean
        get() = prefs.getBoolean(Setting.KEY_CITIES_LOADED, false)
        set(value) = prefs.edit { putBoolean(Setting.KEY_CITIES_LOADED, value) }

    // ════════════════════════════════════════════════════════════════════════
    // Dossiers source
    // ════════════════════════════════════════════════════════════════════════

    var includedFolders: Set<String>
        get() = prefs.getStringSet(Setting.KEY_INCLUDED_FOLDERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(Setting.KEY_INCLUDED_FOLDERS, value) }

    var lastScanTimestamp: Long
        get() = prefs.getLong(Setting.KEY_LAST_SCAN_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(Setting.KEY_LAST_SCAN_TIMESTAMP, value) }

    // ════════════════════════════════════════════════════════════════════════
    // Apparence
    // ════════════════════════════════════════════════════════════════════════

    var themeMode: String
        get() = prefs.getString(Setting.KEY_THEME, Setting.THEME_SYSTEM) ?: Setting.THEME_SYSTEM
        set(value) = prefs.edit { putString(Setting.KEY_THEME, value) }

    var userLanguage: String
        get() = prefs.getString(Setting.KEY_LANGUAGE, Setting.LANGUAGE_SYSTEM) ?: Setting.LANGUAGE_SYSTEM
        set(value) = prefs.edit { putString(Setting.KEY_LANGUAGE, value) }

    var gridColumns: Int
        get() = prefs.getInt(Setting.KEY_GRID_COLUMNS, Setting.DEFAULT_GRID_COLUMNS)
        set(value) = prefs.edit { putInt(Setting.KEY_GRID_COLUMNS, value) }

    // ════════════════════════════════════════════════════════════════════════
    // IA & Reconnaissance faciale
    // ════════════════════════════════════════════════════════════════════════

    var showScores: Boolean
        get() = prefs.getBoolean(Setting.KEY_SHOW_SCORES, DEFAULT_SHOW_SCORES)
        set(value) = prefs.edit { putBoolean(Setting.KEY_SHOW_SCORES, value) }

    var faceThresholdHigh: Float
        get() = prefs.getFloat(Setting.KEY_FACE_THRESHOLD_HIGH, DEFAULT_FACE_THRESHOLD_HIGH)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_THRESHOLD_HIGH, value) }

    var faceThresholdMedium: Float
        get() = prefs.getFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, DEFAULT_FACE_THRESHOLD_MEDIUM)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, value) }

    var faceThresholdNew: Float
        get() = prefs.getFloat(Setting.KEY_FACE_THRESHOLD_NEW, DEFAULT_FACE_THRESHOLD_NEW)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_THRESHOLD_NEW, value) }

    // ════════════════════════════════════════════════════════════════════════
    // Méthodes utilitaires
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Réinitialise tous les paramètres à leurs valeurs par défaut.
     */
    fun resetToDefaults() {
        prefs.edit {
            putString(Setting.KEY_THEME, Setting.THEME_SYSTEM)
            putString(Setting.KEY_LANGUAGE, Setting.LANGUAGE_SYSTEM)
            putInt(Setting.KEY_GRID_COLUMNS, Setting.DEFAULT_GRID_COLUMNS)
            putBoolean(Setting.KEY_SHOW_SCORES, DEFAULT_SHOW_SCORES)
            putFloat(Setting.KEY_FACE_THRESHOLD_HIGH, DEFAULT_FACE_THRESHOLD_HIGH)
            putFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, DEFAULT_FACE_THRESHOLD_MEDIUM)
            putFloat(Setting.KEY_FACE_THRESHOLD_NEW, DEFAULT_FACE_THRESHOLD_NEW)
        }
    }

    /**
     * Synchronise le cache avec des valeurs provenant de la base de données.
     * Appelé au démarrage pour s'assurer que le cache est à jour.
     */
    fun syncFromDatabase(
        theme: String?,
        language: String?,
        gridColumns: Int?,
        showScores: Boolean?,
        thresholdHigh: Float?,
        thresholdMedium: Float?,
        thresholdNew: Float?
    ) {
        prefs.edit {
            theme?.let { putString(Setting.KEY_THEME, it) }
            language?.let { putString(Setting.KEY_LANGUAGE, it) }
            gridColumns?.let { putInt(Setting.KEY_GRID_COLUMNS, it) }
            showScores?.let { putBoolean(Setting.KEY_SHOW_SCORES, it) }
            thresholdHigh?.let { putFloat(Setting.KEY_FACE_THRESHOLD_HIGH, it) }
            thresholdMedium?.let { putFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, it) }
            thresholdNew?.let { putFloat(Setting.KEY_FACE_THRESHOLD_NEW, it) }
        }
    }

    companion object {
        const val DEFAULT_SHOW_SCORES = false
        const val DEFAULT_FACE_THRESHOLD_HIGH = 0.40f
        const val DEFAULT_FACE_THRESHOLD_MEDIUM = 0.60f
        const val DEFAULT_FACE_THRESHOLD_NEW = 0.75f
    }
}
