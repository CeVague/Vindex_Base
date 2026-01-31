package com.cevague.vindex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.SettingsRepository
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
 * - Charger les statistiques de la bibliothèque de manière asynchrone
 * - Fournir les informations de l'application (version, etc.)
 * - Gérer la synchronisation cache <-> database
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val personRepository: PersonRepository,
    private val albumRepository: AlbumRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsCache: SettingsCache
) : ViewModel() {

    // ════════════════════════════════════════════════════════════════════════
    // Statistiques de la bibliothèque
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Statistiques combinées de la bibliothèque.
     * Toutes les données sont chargées en parallèle via combine().
     */
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

    /**
     * Espace disque utilisé par les photos (calculé une seule fois).
     */
    private val _storageUsed = MutableStateFlow<Long>(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    // ════════════════════════════════════════════════════════════════════════
    // Informations de l'application
    // ════════════════════════════════════════════════════════════════════════

    val appVersion: String = BuildConfig.VERSION_NAME
    val appVersionCode: Int = BuildConfig.VERSION_CODE

    // ════════════════════════════════════════════════════════════════════════
    // Initialisation
    // ════════════════════════════════════════════════════════════════════════

    init {
        loadStorageUsed()
    }

    private fun loadStorageUsed() {
        viewModelScope.launch {
            _storageUsed.value = photoRepository.getTotalStorageUsed()
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Actions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sauvegarde une valeur string dans les settings.
     */
    fun saveStringSetting(key: String, value: String) {
        viewModelScope.launch {
            settingsRepository.setValue(key, value)
            // Mettre à jour le cache selon la clé
            when (key) {
                com.cevague.vindex.data.database.entity.Setting.KEY_THEME ->
                    settingsCache.themeMode = value
                com.cevague.vindex.data.database.entity.Setting.KEY_LANGUAGE ->
                    settingsCache.userLanguage = value
            }
        }
    }

    /**
     * Sauvegarde une valeur int dans les settings.
     */
    fun saveIntSetting(key: String, value: Int) {
        viewModelScope.launch {
            settingsRepository.setValue(key, value.toString())
            when (key) {
                com.cevague.vindex.data.database.entity.Setting.KEY_GRID_COLUMNS ->
                    settingsCache.gridColumns = value
            }
        }
    }

    /**
     * Sauvegarde une valeur boolean dans les settings.
     */
    fun saveBooleanSetting(key: String, value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setValue(key, value.toString())
            when (key) {
                com.cevague.vindex.data.database.entity.Setting.KEY_SHOW_SCORES ->
                    settingsCache.showScores = value
            }
        }
    }

    /**
     * Sauvegarde une valeur float dans les settings (pour les thresholds).
     */
    fun saveFloatSetting(key: String, value: Float) {
        viewModelScope.launch {
            settingsRepository.setValue(key, value.toString())
            when (key) {
                com.cevague.vindex.data.database.entity.Setting.KEY_FACE_THRESHOLD_HIGH ->
                    settingsCache.faceThresholdHigh = value
                com.cevague.vindex.data.database.entity.Setting.KEY_FACE_THRESHOLD_MEDIUM ->
                    settingsCache.faceThresholdMedium = value
                com.cevague.vindex.data.database.entity.Setting.KEY_FACE_THRESHOLD_NEW ->
                    settingsCache.faceThresholdNew = value
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Data classes
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Statistiques de la bibliothèque.
     */
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
// Extensions utilitaires
// ════════════════════════════════════════════════════════════════════════════

/**
 * Formate un nombre avec les séparateurs de milliers selon la locale.
 */
fun Int.formatNumber(): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(this)
}

/**
 * Formate une taille en bytes en une chaîne lisible (KB, MB, GB).
 */
fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    val size = this / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", size, units[digitGroups])
}
