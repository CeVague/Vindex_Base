package com.cevague.vindex.data.repository

import com.cevague.vindex.data.database.dao.SettingDao
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.FastSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val settingDao: SettingDao) {

    // Generic access

    fun getValue(key: String): Flow<String?> = settingDao.getValue(key)

    suspend fun getValueOnce(key: String): String? = settingDao.getValueOnce(key)

    suspend fun setValue(key: String, value: String) = settingDao.setValue(key, value)

    suspend fun delete(key: String) = settingDao.delete(key)

    suspend fun deleteAll() = settingDao.deleteAll()

    // Source folder

    fun getSourceFolderUri(): Flow<String?> = getValue(Setting.KEY_SOURCE_FOLDER_URI)

    suspend fun getSourceFolderUriOnce(): String? = getValueOnce(Setting.KEY_SOURCE_FOLDER_URI)

    suspend fun setSourceFolderUri(uri: String) {
        setValue(Setting.KEY_SOURCE_FOLDER_URI, uri) // BDD
        FastSettings.sourceFolderUri = uri // Miroir
    }

    // Grid columns

    fun getGridColumns(): Flow<Int> = getValue(Setting.KEY_GRID_COLUMNS).map {
        it?.toIntOrNull() ?: DEFAULT_GRID_COLUMNS
    }

    suspend fun getGridColumnsOnce(): Int =
        getValueOnce(Setting.KEY_GRID_COLUMNS)?.toIntOrNull() ?: DEFAULT_GRID_COLUMNS

    suspend fun setGridColumns(columns: Int) =
        setValue(Setting.KEY_GRID_COLUMNS, columns.toString())

    // Theme

    fun getTheme(): Flow<String> = getValue(Setting.KEY_THEME).map {
        it ?: Setting.THEME_SYSTEM
    }

    suspend fun getThemeOnce(): String =
        getValueOnce(Setting.KEY_THEME) ?: Setting.THEME_SYSTEM

    suspend fun setTheme(theme: String) = setValue(Setting.KEY_THEME, theme)

    // Language

    fun getLanguage(): Flow<String> = getValue(Setting.KEY_LANGUAGE).map {
        it ?: Setting.LANGUAGE_SYSTEM
    }

    suspend fun getLanguageOnce(): String =
        getValueOnce(Setting.KEY_LANGUAGE) ?: Setting.LANGUAGE_SYSTEM

    suspend fun setLanguage(language: String) = setValue(Setting.KEY_LANGUAGE, language)

    // Show similarity scores (debug/advanced)

    fun getShowScores(): Flow<Boolean> = getValue(Setting.KEY_SHOW_SCORES).map {
        it?.toBoolean() ?: DEFAULT_SHOW_SCORES
    }

    suspend fun getShowScoresOnce(): Boolean =
        getValueOnce(Setting.KEY_SHOW_SCORES)?.toBoolean() ?: DEFAULT_SHOW_SCORES

    suspend fun setShowScores(show: Boolean) =
        setValue(Setting.KEY_SHOW_SCORES, show.toString())

    // Face recognition thresholds

    fun getFaceThresholdHigh(): Flow<Float> = getValue(Setting.KEY_FACE_THRESHOLD_HIGH).map {
        it?.toFloatOrNull() ?: DEFAULT_FACE_THRESHOLD_HIGH
    }

    suspend fun getFaceThresholdHighOnce(): Float =
        getValueOnce(Setting.KEY_FACE_THRESHOLD_HIGH)?.toFloatOrNull()
            ?: DEFAULT_FACE_THRESHOLD_HIGH

    suspend fun setFaceThresholdHigh(threshold: Float) =
        setValue(Setting.KEY_FACE_THRESHOLD_HIGH, threshold.toString())

    fun getFaceThresholdMedium(): Flow<Float> = getValue(Setting.KEY_FACE_THRESHOLD_MEDIUM).map {
        it?.toFloatOrNull() ?: DEFAULT_FACE_THRESHOLD_MEDIUM
    }

    suspend fun getFaceThresholdMediumOnce(): Float =
        getValueOnce(Setting.KEY_FACE_THRESHOLD_MEDIUM)?.toFloatOrNull()
            ?: DEFAULT_FACE_THRESHOLD_MEDIUM

    suspend fun setFaceThresholdMedium(threshold: Float) =
        setValue(Setting.KEY_FACE_THRESHOLD_MEDIUM, threshold.toString())

    fun getFaceThresholdNew(): Flow<Float> = getValue(Setting.KEY_FACE_THRESHOLD_NEW).map {
        it?.toFloatOrNull() ?: DEFAULT_FACE_THRESHOLD_NEW
    }

    suspend fun getFaceThresholdNewOnce(): Float =
        getValueOnce(Setting.KEY_FACE_THRESHOLD_NEW)?.toFloatOrNull()
            ?: DEFAULT_FACE_THRESHOLD_NEW

    suspend fun setFaceThresholdNew(threshold: Float) =
        setValue(Setting.KEY_FACE_THRESHOLD_NEW, threshold.toString())

    companion object {
        const val DEFAULT_GRID_COLUMNS = 3
        const val DEFAULT_SHOW_SCORES = false
        const val DEFAULT_FACE_THRESHOLD_HIGH = 0.40f
        const val DEFAULT_FACE_THRESHOLD_MEDIUM = 0.60f
        const val DEFAULT_FACE_THRESHOLD_NEW = 0.75f
    }
}
