package com.cevague.vindex.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key-value storage for application settings.
 */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey
    val key: String,

    val value: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val KEY_FIRST_RUN = "is_first_run"
        const val KEY_CITIES_LOADED = "is_cities_loaded"
        const val KEY_SOURCE_FOLDER_URI = "source_folder_uri"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_SHOW_SCORES = "show_scores"
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
    }
}
