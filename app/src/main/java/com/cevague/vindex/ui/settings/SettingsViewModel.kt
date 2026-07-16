package com.cevague.vindex.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.search.asFloatArray
import com.cevague.vindex.search.dotProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    @ApplicationContext private val context: Context,
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

    /**
     * Ré-analyse des visages à la demande. Sans elle, une amélioration du pipeline
     * (crop, alignement, seuils du détecteur) resterait **invisible** : la file est
     * un `NOT EXISTS` sur le composite `détecteur_embedder`, qui ne bouge pas quand
     * c'est le *code* qui change. Seul un changement de modèle relançait le calcul.
     */
    fun startFaceReanalysis() {
        viewModelScope.launch { scanManager.startFaceReanalysis() }
    }

    /**
     * Debug : exporte **toutes les paires** de visages identifiés — même personne ou
     * non, et similarité — en CSV.
     *
     * Ce que ça permet et que rien d'autre ne permet : calibrer sur une **vérité
     * terrain**. Le log `CALIBRATION` ne donne qu'une distribution dont il faut
     * *deviner* qui est qui ; ici l'appartenance est connue, donc la frontière se
     * calcule au lieu de s'estimer. À utiliser après un regroupement manuel
     * (`autoClusteringEnabled = false`), sans quoi on ne mesure que l'accord de
     * l'automate avec lui-même.
     *
     * O(n²) assumé : c'est un outil de galerie de test, d'où le garde-fou.
     */
    suspend fun exportFaceSimilarities(): String? = withContext(Dispatchers.IO) {
        // Hors personnes masquées : deux visages du même passant peuvent finir dans
        // deux groupes masqués distincts, ce qui inscrirait « personnes différentes »
        // dans la vérité terrain alors que c'est la même. Mieux vaut ne pas les
        // compter que compter faux.
        val faces = personRepository.getFacesForCalibration()
        if (faces.size < 2) return@withContext null
        if (faces.size > MAX_EXPORT_FACES) {
            Log.w(TAG, "Export refusé : ${faces.size} visages, ${MAX_EXPORT_FACES} max")
            return@withContext null
        }

        val vectors = faces.map { face ->
            val blob = face.embedding!!
            blob.asFloatArray(blob.size / Float.SIZE_BYTES)
        }

        val file = File(context.getExternalFilesDir(null), "face_pairs.csv")
        file.bufferedWriter().use { out ->
            out.write("same_person,similarity,face_a,face_b,person_a,person_b,type_a,type_b\n")
            for (i in faces.indices) {
                for (j in i + 1 until faces.size) {
                    val same = if (faces[i].personId == faces[j].personId) 1 else 0
                    val similarity = dotProduct(vectors[i], vectors[j])
                    out.write(
                        "$same,$similarity,${faces[i].id},${faces[j].id}," +
                                "${faces[i].personId},${faces[j].personId}," +
                                "${faces[i].assignmentType},${faces[j].assignmentType}\n"
                    )
                }
            }
        }
        Log.i(TAG, "Export : ${faces.size} visages -> ${file.absolutePath}")
        file.absolutePath
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

    // La persistance des réglages est passée dans SettingsDataStore : le ViewModel
    // n'était qu'un relais vers SettingsCache, et deux écrans de préférences ont
    // désormais besoin du même branchement.

    private companion object {
        const val TAG = "SettingsViewModel"

        /** ~2 M paires, quelques dizaines de Mo de CSV : au-delà, l'O(n²) n'a plus de sens. */
        const val MAX_EXPORT_FACES = 2000
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
