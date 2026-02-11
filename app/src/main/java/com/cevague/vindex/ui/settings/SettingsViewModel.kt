package com.cevague.vindex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.SettingsRepository
import com.cevague.vindex.util.ScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel pour l'écran des paramètres.
 *
 * Responsabilités:
 * - Fournir les statistiques de la bibliothèque et l'espace disque
 * - Gérer la persistance des réglages (Database + Cache)
 * - Coordonner les actions globales (Scan, Reset)
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val personRepository: PersonRepository,
    private val albumRepository: AlbumRepository,
    private val settingsRepository: SettingsRepository,
    val settingsCache: SettingsCache, // Gardé public pour le DataStore du Fragment
    private val scanManager: ScanManager
) : ViewModel() {

    // ════════════════════════════════════════════════════════════════════════
    // Statistiques
    // ════════════════════════════════════════════════════════════════════════

    val libraryStats: StateFlow<LibraryStats> = combine(
        photoRepository.getPhotoCount(),
        photoRepository.getVisiblePhotoCount(),
        personRepository.getPersonCount(),
        personRepository.getUnnamedPersonCount(),
        personRepository.getPendingFaceCount(),
        albumRepository.getAlbumCount()
    ) { values ->
        LibraryStats(
            totalPhotos = values[0] as Int,
            visiblePhotos = values[1] as Int,
            totalPeople = values[2] as Int,
            unnamedPeople = values[3] as Int,
            pendingFaces = values[4] as Int,
            totalAlbums = values[5] as Int
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryStats()
    )

    private val _storageUsed = MutableStateFlow<Long>(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    // ════════════════════════════════════════════════════════════════════════
    // App Info
    // ════════════════════════════════════════════════════════════════════════

    val appVersion: String = BuildConfig.VERSION_NAME
    val appVersionCode: Int = BuildConfig.VERSION_CODE

    init {
        loadStorageUsed()
    }

    private fun loadStorageUsed() {
        viewModelScope.launch {
            _storageUsed.value = photoRepository.getTotalStorageUsed()
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Actions Métier
    // ════════════════════════════════════════════════════════════════════════

    fun startFullScan() {
        scanManager.startFullScan()
    }

    fun resetDatabase() {
        viewModelScope.launch {
            photoRepository.deleteAll()
            personRepository.deleteAllPersons()
            personRepository.deleteAllFaces()
            albumRepository.deleteAll()
            settingsCache.lastScanTimestamp = 0L
            
            // Relancer un scan pour reconstruire la base à partir des fichiers réels
            scanManager.startFullScan()
        }
    }

    fun resetSettings() {
        settingsCache.resetToDefaults()
        // On pourrait aussi vider la table 'settings' de la DB ici
    }

    // ════════════════════════════════════════════════════════════════════════
    // Persistance des réglages
    // ════════════════════════════════════════════════════════════════════════

    fun saveStringSetting(key: String, value: String) {
        // Mise à jour immédiate du cache (synchrone pour l'UI)
        when (key) {
            Setting.KEY_THEME -> settingsCache.themeMode = value
            Setting.KEY_LANGUAGE -> settingsCache.userLanguage = value
            Setting.KEY_FACE_THRESHOLD_HIGH -> value.toFloatOrNull()?.let { settingsCache.faceThresholdHigh = it }
            Setting.KEY_FACE_THRESHOLD_MEDIUM -> value.toFloatOrNull()?.let { settingsCache.faceThresholdMedium = it }
            Setting.KEY_FACE_THRESHOLD_NEW -> value.toFloatOrNull()?.let { settingsCache.faceThresholdNew = it }
        }

        // Sauvegarde DB asynchrone
        viewModelScope.launch {
            settingsRepository.setValue(key, value)
        }
    }

    fun saveIntSetting(key: String, value: Int) {
        if (key == Setting.KEY_GRID_COLUMNS) {
            settingsCache.gridColumns = value
        }
        viewModelScope.launch {
            settingsRepository.setValue(key, value.toString())
        }
    }

    fun saveBooleanSetting(key: String, value: Boolean) {
        if (key == Setting.KEY_SHOW_SCORES) {
            settingsCache.showScores = value
        }
        viewModelScope.launch {
            settingsRepository.setValue(key, value.toString())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Data Classes
    // ════════════════════════════════════════════════════════════════════════

    data class LibraryStats(
        val totalPhotos: Int = 0,
        val visiblePhotos: Int = 0,
        val totalPeople: Int = 0,
        val unnamedPeople: Int = 0,
        val pendingFaces: Int = 0,
        val totalAlbums: Int = 0
    ) {
        val hiddenPhotos: Int get() = totalPhotos - visiblePhotos
        val namedPeople: Int get() = totalPeople - unnamedPeople
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Extensions
// ════════════════════════════════════════════════════════════════════════════

fun Int.formatNumber(): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(this)
}

fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    val size = this / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", size, units[digitGroups])
}
