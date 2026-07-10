package com.cevague.vindex.data.database.entity

/**
 * Registre des clés de préférences.
 *
 * Les préférences sont stockées exclusivement dans SharedPreferences via
 * [com.cevague.vindex.data.local.SettingsCache] (lectures synchrones nécessaires
 * au layout, données purement locales). Cet objet ne fait que centraliser les
 * clés et leurs valeurs par défaut ; il ne correspond plus à une table Room.
 */
object Setting {
    const val KEY_FIRST_RUN = "is_first_run"
    const val KEY_CITIES_LOADED = "is_cities_loaded"
    const val KEY_GRID_COLUMNS = "grid_columns"
    const val DEFAULT_GRID_COLUMNS = 3
    const val KEY_THEME = "theme"
    const val KEY_LANGUAGE = "language"
    const val KEY_SHOW_SCORES = "show_scores"
    const val KEY_SEARCH_THRESHOLD = "search_threshold"
    const val KEY_FACE_THRESHOLD_HIGH = "face_threshold_high"
    const val KEY_FACE_THRESHOLD_MEDIUM = "face_threshold_medium"
    const val KEY_FACE_THRESHOLD_NEW = "face_threshold_new"
    const val KEY_LAST_SCAN_TIMESTAMP = "last_scan_timestamp"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_ENGLISH = "en"

    const val KEY_INCLUDED_FOLDERS = "included_folders"
    const val KEY_IS_CONFIGURED = "is_configured"
}
