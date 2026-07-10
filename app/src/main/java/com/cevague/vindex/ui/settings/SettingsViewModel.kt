package com.cevague.vindex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.StorageRepository
import com.cevague.vindex.util.MediaScanner
import com.cevague.vindex.util.ScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel pour l'écran des paramètres.
 *
 * Responsabilités:
 * - Fournir les statistiques de la bibliothèque et l'espace disque
 * - Gérer la persistance des réglages (SharedPreferences via SettingsCache)
 * - Coordonner les actions globales (Scan, Reset)
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val personRepository: PersonRepository,
    private val albumRepository: AlbumRepository,
    val settingsCache: SettingsCache, // Gardé public pour le DataStore du Fragment
    private val scanManager: ScanManager,
    private val mediaScanner: MediaScanner,
    private val storageRepository: StorageRepository
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

    private val _storage = MutableStateFlow(StorageBreakdown())
    val storage: StateFlow<StorageBreakdown> = _storage.asStateFlow()

    // ════════════════════════════════════════════════════════════════════════
    // App Info
    // ════════════════════════════════════════════════════════════════════════

    val appVersion: String = BuildConfig.VERSION_NAME
    val appVersionCode: Int = BuildConfig.VERSION_CODE

    init {
        loadStorage()
    }

    /** Empreinte disque de Vindex (modèles + base), calculée hors thread principal. */
    private fun loadStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                StorageBreakdown(
                    modelsBytes = storageRepository.modelsSize(),
                    databaseBytes = storageRepository.databaseSize()
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Actions Métier
    // ════════════════════════════════════════════════════════════════════════

    fun startFullScan() {
        scanManager.startFullScan()
    }

    /** Dossiers d'images disponibles (pour l'édition des dossiers indexés). */
    suspend fun availableFolders(): List<MediaScanner.FolderInfo> =
        withContext(Dispatchers.IO) { mediaScanner.listImageFolders() }

    /**
     * Applique une nouvelle sélection de dossiers puis relance un scan complet :
     * les photos des dossiers retirés sont supprimées (cascade FK sur embeddings/
     * visages/albums), les nouvelles indexées (workers incrémentaux), et le
     * CleanupWorker recale les personnes.
     */
    fun applyIncludedFolders(folders: Set<String>) {
        settingsCache.includedFolders = folders
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
    }

    // ════════════════════════════════════════════════════════════════════════
    // Persistance des réglages
    // ════════════════════════════════════════════════════════════════════════

    fun saveStringSetting(key: String, value: String) {
        when (key) {
            Setting.KEY_THEME -> settingsCache.themeMode = value
            Setting.KEY_LANGUAGE -> settingsCache.userLanguage = value
            Setting.KEY_FACE_THRESHOLD_HIGH -> value.toFloatOrNull()
                ?.let { settingsCache.faceThresholdHigh = it }

            Setting.KEY_FACE_THRESHOLD_MEDIUM -> value.toFloatOrNull()
                ?.let { settingsCache.faceThresholdMedium = it }

            Setting.KEY_FACE_THRESHOLD_NEW -> value.toFloatOrNull()
                ?.let { settingsCache.faceThresholdNew = it }
        }
    }

    fun saveIntSetting(key: String, value: Int) {
        if (key == Setting.KEY_GRID_COLUMNS) {
            settingsCache.gridColumns = value
        }
    }

    fun saveBooleanSetting(key: String, value: Boolean) {
        if (key == Setting.KEY_SHOW_SCORES) {
            settingsCache.showScores = value
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

    /** Empreinte disque propre à Vindex (hors photos). */
    data class StorageBreakdown(
        val modelsBytes: Long = 0L,
        val databaseBytes: Long = 0L
    ) {
        val totalBytes: Long get() = modelsBytes + databaseBytes
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
