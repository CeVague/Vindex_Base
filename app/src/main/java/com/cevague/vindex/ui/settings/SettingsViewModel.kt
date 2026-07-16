package com.cevague.vindex.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.ai.DetectedFace
import com.cevague.vindex.ai.boxAspect
import com.cevague.vindex.ai.edgeMargin
import com.cevague.vindex.ai.eyeDistance
import com.cevague.vindex.ai.FaceEngine
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.AiModelRepository
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
    private val aiModelRepository: AiModelRepository,
    private val faceEngine: FaceEngine,
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

    /** Un visage étiqueté, prêt pour l'export : sa personne (+ nom) et son vecteur. */
    private data class CalibFace(
        val faceId: Long,
        val personId: Long,
        val personName: String,
        val embedding: FloatArray
    )

    /**
     * Debug : exporte **toutes les paires** de visages identifiés — même personne ou
     * non, similarité, et **noms** — en CSV, avec l'embedder actif (vecteurs déjà en
     * base).
     *
     * Ce que ça permet et que rien d'autre ne permet : calibrer sur une **vérité
     * terrain**. Le log `CALIBRATION` ne donne qu'une distribution dont il faut
     * *deviner* qui est qui ; ici l'appartenance est connue, donc la frontière se
     * calcule au lieu de s'estimer. À utiliser après un regroupement manuel
     * (`autoClusteringEnabled = false`), sans quoi on ne mesure que l'accord de
     * l'automate avec lui-même.
     */
    suspend fun exportFaceSimilarities(): String? = withContext(Dispatchers.IO) {
        // Hors personnes masquées : deux visages du même passant peuvent finir dans
        // deux groupes masqués distincts, ce qui inscrirait « personnes différentes »
        // dans la vérité terrain alors que c'est la même. Mieux vaut ne pas les
        // compter que compter faux.
        val names = personNames()
        val faces = personRepository.getFacesForCalibration()
            .filter { it.embedding != null }
            .map { face ->
                val blob = face.embedding!!
                CalibFace(
                    faceId = face.id,
                    personId = face.personId ?: 0,
                    personName = names[face.personId] ?: "",
                    embedding = blob.asFloatArray(blob.size / Float.SIZE_BYTES)
                )
            }
        writePairs(faces, "face_pairs.csv")
    }

    /**
     * Debug : le **même** export, mais recalculé avec **chaque embedder importé** —
     * un CSV par modèle. C'est ainsi qu'on compare des modèles (EdgeFace S, XXS…) sur
     * une vérité terrain fixe.
     *
     * ⚠ Les vecteurs sont **recalculés**, jamais lus en base, et **rien n'est
     * activé** : activer un modèle de visages déclencherait la ré-analyse, qui
     * effacerait justement le regroupement manuel qu'on veut mesurer. Le détecteur
     * actif est réutilisé tel quel (on ne fait varier que l'embedder), et chaque
     * détection est ré-appariée au visage étiqueté par recouvrement de boîte.
     */
    suspend fun exportEmbedderComparison(): List<String> = withContext(Dispatchers.IO) {
        val models = aiModelRepository.getModelsByTypeOnce(AiModel.TYPE_FACE_EMBEDDING)
        if (models.isEmpty()) return@withContext emptyList()

        val names = personNames()
        // Groupées par photo : une détection re-tourne tous les visages d'une image
        // d'un coup, autant ne la lancer qu'une fois par photo.
        val byPhoto = personRepository.getLabeledFacesWithPhoto().groupBy { it.filePath }

        val written = mutableListOf<String>()
        writeMetrics(byPhoto, names)?.let { written += it }
        try {
            for (model in models) {
                val faces = mutableListOf<CalibFace>()
                for ((uri, stored) in byPhoto) {
                    val analyzed = try {
                        faceEngine.analyzeFacesWith(uri, model)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ré-embarquement impossible ($uri, ${model.modelName})", e)
                        continue
                    }
                    // Même détecteur → mêmes boîtes : chaque visage stocké retrouve sa
                    // détection par le meilleur recouvrement.
                    for (face in stored) {
                        val match = analyzed.maxByOrNull { faceBoxIou(face, it.detected) } ?: continue
                        if (faceBoxIou(face, match.detected) < MATCH_IOU) continue
                        faces += CalibFace(
                            face.id, face.personId, names[face.personId] ?: "", match.embedding
                        )
                    }
                }
                val safe = model.modelName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                writePairs(faces, "face_pairs_$safe.csv")?.let { written += it }
            }
        } finally {
            faceEngine.releaseCalibrationSessions()
        }
        written
    }

    /**
     * Une ligne par visage : tout ce qui peut expliquer **pourquoi** un visage se
     * reconnaît mal, indépendamment de qui il est.
     *
     * Écrit une seule fois, pas par modèle : ces mesures ne dépendent que du
     * détecteur et de la géométrie. À joindre aux `face_pairs_*.csv` sur `face_id`.
     */
    private suspend fun writeMetrics(
        byPhoto: Map<String, List<FaceDao.LabeledFace>>,
        names: Map<Long, String>
    ): String? {
        val file = File(context.getExternalFilesDir(null), "face_metrics.csv")
        var rows = 0
        file.bufferedWriter().use { out ->
            out.write(
                "face_id,person_id,name,quality,det_score,det_cls,det_obj," +
                        "box_w,box_h,box_aspect,face_px,eye_dist_px,photo_px,edge_margin," +
                        "align_rmse,align_scale,align_roll_deg,yaw,pitch," +
                        "blur,photo_blur,brightness,contrast\n"
            )
            for ((uri, stored) in byPhoto) {
                val metrics = try {
                    faceEngine.faceMetrics(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Métriques impossibles ($uri)", e)
                    continue
                }
                for (face in stored) {
                    val m = metrics.maxByOrNull { faceBoxIou(face, it.detected) } ?: continue
                    if (faceBoxIou(face, m.detected) < MATCH_IOU) continue

                    val d = m.detected
                    val pw = face.photoWidth ?: 0
                    val ph = face.photoHeight ?: 0
                    // Pixels réels : c'est la largeur d'origine qui décide, pas la
                    // fraction de l'image — un visage à 5 % d'un 48 Mpx reste net.
                    val facePx = (d.boxRight - d.boxLeft) * pw
                    val eyePx = eyeDistance(d.landmarks) * pw
                    out.write(
                        "${face.id},${face.personId},${names[face.personId] ?: ""}," +
                                "${m.quality},${d.score},${d.clsScore},${d.objScore}," +
                                "${d.boxRight - d.boxLeft},${d.boxBottom - d.boxTop}," +
                                "${boxAspect(d.boxLeft, d.boxTop, d.boxRight, d.boxBottom)}," +
                                "$facePx,$eyePx,${pw.toLong() * ph}," +
                                "${edgeMargin(d.boxLeft, d.boxTop, d.boxRight, d.boxBottom)}," +
                                "${m.alignRmse},${m.alignScale},${m.alignRollDeg},${m.yaw},${m.pitch}," +
                                "${m.blur},${m.photoBlur},${m.brightness},${m.contrast}\n"
                    )
                    rows++
                }
            }
        }
        if (rows == 0) return null
        Log.i(TAG, "Métriques : $rows visages -> ${file.absolutePath}")
        return file.absolutePath
    }

    private suspend fun personNames(): Map<Long, String> =
        personRepository.getAllPersonsOnce().associate { it.id to (it.name ?: "") }

    private fun writePairs(faces: List<CalibFace>, fileName: String): String? {
        if (faces.size < 2) return null
        if (faces.size > MAX_EXPORT_FACES) {
            Log.w(TAG, "Export refusé : ${faces.size} visages, $MAX_EXPORT_FACES max")
            return null
        }
        val file = File(context.getExternalFilesDir(null), fileName)
        file.bufferedWriter().use { out ->
            out.write("same_person,similarity,face_a,face_b,person_a,person_b,name_a,name_b\n")
            for (i in faces.indices) {
                for (j in i + 1 until faces.size) {
                    val a = faces[i]
                    val b = faces[j]
                    val same = if (a.personId == b.personId) 1 else 0
                    val similarity = dotProduct(a.embedding, b.embedding)
                    out.write(
                        "$same,$similarity,${a.faceId},${b.faceId}," +
                                "${a.personId},${b.personId},${a.personName},${b.personName}\n"
                    )
                }
            }
        }
        Log.i(TAG, "Export : ${faces.size} visages -> ${file.absolutePath}")
        return file.absolutePath
    }

    /** Recouvrement d'une boîte stockée et d'une boîte détectée, normalisées 0-1. */
    private fun faceBoxIou(a: FaceDao.LabeledFace, b: DetectedFace): Float {
        val interW = (minOf(a.boxRight, b.boxRight) - maxOf(a.boxLeft, b.boxLeft)).coerceAtLeast(0f)
        val interH = (minOf(a.boxBottom, b.boxBottom) - maxOf(a.boxTop, b.boxTop)).coerceAtLeast(0f)
        val inter = interW * interH
        val union = (a.boxRight - a.boxLeft) * (a.boxBottom - a.boxTop) +
                (b.boxRight - b.boxLeft) * (b.boxBottom - b.boxTop) - inter
        return if (union <= 0f) 0f else inter / union
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

        /**
         * Recouvrement minimal pour ré-apparier une détection à un visage stocké. Le
         * détecteur étant le même, la bonne paire frôle 1,0 — ce seuil ne sert qu'à
         * rejeter l'absence de correspondance, pas à départager.
         */
        const val MATCH_IOU = 0.5f
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
