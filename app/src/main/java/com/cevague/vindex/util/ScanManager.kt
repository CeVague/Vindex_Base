package com.cevague.vindex.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.worker.AIAnalysisWorker
import com.cevague.vindex.worker.CityImportWorker
import com.cevague.vindex.worker.CleanupWorker
import com.cevague.vindex.worker.ClipIndexWorker
import com.cevague.vindex.worker.DiscoveryWorker
import com.cevague.vindex.worker.FaceAnalysisWorker
import com.cevague.vindex.worker.MetadataWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository,
    private val personRepository: PersonRepository
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Scan léger (swipe de la galerie) : `KEEP` est ici la bonne réponse. Si une
     * chaîne tourne déjà, elle découvrira les mêmes photos — la relancer ne ferait
     * que la faire repartir de zéro. Et surtout, cette chaîne n'a ni l'import des
     * villes ni les albums IA : la laisser remplacer un scan complet en cours
     * ferait sauter ces étapes en silence.
     */
    fun startGalleryScan() {
        val discoveryReq = OneTimeWorkRequestBuilder<DiscoveryWorker>()
            .addTag("SCAN_TAG")
            .build()

        val metadataReq = OneTimeWorkRequestBuilder<MetadataWorker>()
            .addTag("SCAN_TAG")
            .build()

        val clipReq = OneTimeWorkRequestBuilder<ClipIndexWorker>()
            .addTag("SCAN_TAG")
            .build()

        val faceReq = OneTimeWorkRequestBuilder<FaceAnalysisWorker>()
            .addTag("SCAN_TAG")
            .build()

        val cleanupReq = OneTimeWorkRequestBuilder<CleanupWorker>()
            .addTag("SCAN_TAG")
            .build()

        workManager
            .beginUniqueWork(UNIQUE_CHAIN, ExistingWorkPolicy.KEEP, discoveryReq)
            .then(metadataReq)
            .then(clipReq)
            .then(faceReq)
            .then(cleanupReq)
            .enqueue()
    }

    /**
     * Scan complet : toujours demandé **explicitement** (accueil, « Rescanner la
     * galerie », changement de dossiers indexés, réinitialisation de la base) —
     * donc `REPLACE`, jamais `KEEP`.
     *
     * `KEEP` jetait la demande sans un mot tant qu'une chaîne du même nom n'était
     * pas terminée : pendant qu'elle tourne, mais aussi pendant un backoff de
     * `Result.retry()`, où rien ne semble tourner. D'où des rescans « aléatoires ».
     *
     * Ce n'est pas qu'un problème de confort : l'appelant vient de changer les
     * prémisses sous la chaîne en cours (nouveaux dossiers, base vidée). La
     * laisser finir, c'est la laisser travailler sur du périmé — il faut donc
     * bien la remplacer, pas s'ajouter derrière elle.
     */
    fun startFullScan() {
        val discoveryReq = OneTimeWorkRequestBuilder<DiscoveryWorker>()
            .addTag("SCAN_TAG")
            .build()

        val citiesReq = OneTimeWorkRequestBuilder<CityImportWorker>()
            .addTag("SCAN_TAG")
            .build()

        val metadataReq = OneTimeWorkRequestBuilder<MetadataWorker>()
            .addTag("SCAN_TAG")
            .build()

        val clipReq = OneTimeWorkRequestBuilder<ClipIndexWorker>()
            .addTag("SCAN_TAG")
            .build()

        val aiReq = OneTimeWorkRequestBuilder<AIAnalysisWorker>()
            .addTag("SCAN_TAG")
            .build()

        val faceReq = OneTimeWorkRequestBuilder<FaceAnalysisWorker>()
            .addTag("SCAN_TAG")
            .build()

        val cleanupReq = OneTimeWorkRequestBuilder<CleanupWorker>()
            .addTag("SCAN_TAG")
            .build()

        workManager
            .beginUniqueWork(UNIQUE_CHAIN, ExistingWorkPolicy.REPLACE, discoveryReq)
            .then(citiesReq)
            .then(metadataReq)
            .then(clipReq)
            .then(aiReq)
            .then(faceReq)
            .then(cleanupReq)
            .enqueue()
    }

    fun cancelAllScans() {
        workManager.cancelAllWorkByTag("SCAN_TAG")
    }

    /**
     * Ré-indexation CLIP après changement de modèle. Chaîne annulée **et attendue**
     * avant la suppression : un ClipIndexWorker encore vivant écrirait des vecteurs
     * de l'ancien modèle après le vidage. Même nom unique que les scans — deux
     * chaînes de noms différents tourneraient en parallèle sur la même file.
     */
    suspend fun startClipReindexing() {
        cancelRunningChain()
        photoRepository.deleteAnalysesByType(PhotoAnalysis.TYPE_CLIP_EMBEDDING)
        val clipReq = OneTimeWorkRequestBuilder<ClipIndexWorker>()
            .addTag("SCAN_TAG")
            .build()
        workManager.enqueueUniqueWork(UNIQUE_CHAIN, ExistingWorkPolicy.REPLACE, clipReq)
    }

    /**
     * Ré-analyse complète des visages, après changement de détecteur ou d'embedder.
     *
     * Les deux invalident tout : `model_name` est le composite `détecteur_embedder`,
     * donc la file `NOT EXISTS` reprend d'elle-même toutes les photos. Ce qu'elle ne
     * peut pas deviner, c'est que les **centroïdes** des personnes appartiennent à
     * l'ancien espace vectoriel — d'où le `resetFaceData()` avant de lancer quoi que
     * ce soit, sinon les nouveaux visages seraient comparés à d'anciens repères.
     *
     * `CleanupWorker` chaîné derrière : sans visages, les compteurs sont faux et les
     * personnes non nommées sont devenues vides.
     */
    suspend fun startFaceReanalysis() {
        // L'annulation est attendue AVANT le reset : un FaceAnalysisWorker de la
        // chaîne précédente encore vivant réinsérerait des visages de l'ancien
        // espace vectoriel juste après le vidage.
        cancelRunningChain()
        photoRepository.deleteAnalysesByType(PhotoAnalysis.TYPE_FACES)
        personRepository.resetFaceData()

        val faceReq = OneTimeWorkRequestBuilder<FaceAnalysisWorker>()
            .addTag("SCAN_TAG")
            .build()

        val cleanupReq = OneTimeWorkRequestBuilder<CleanupWorker>()
            .addTag("SCAN_TAG")
            .build()

        workManager
            .beginUniqueWork(UNIQUE_CHAIN, ExistingWorkPolicy.REPLACE, faceReq)
            .then(cleanupReq)
            .enqueue()
    }

    /**
     * Indexation initiale (premier modèle) : indexe les photos manquantes sans
     * rien supprimer. La file du worker est « photos sans vecteur du modèle actif »,
     * donc seuls les nouveaux éléments sont calculés.
     */
    fun startClipIndexing() {
        // KEEP sous le nom commun : si une chaîne tourne déjà, elle contient de
        // toute façon un ClipIndexWorker — jeter la demande est le bon comportement.
        val clipReq = OneTimeWorkRequestBuilder<ClipIndexWorker>()
            .addTag("SCAN_TAG")
            .build()
        workManager.enqueueUniqueWork(UNIQUE_CHAIN, ExistingWorkPolicy.KEEP, clipReq)
    }

    /**
     * Annule la chaîne unique et **attend** que l'annulation soit effective.
     * `Operation.await()` ne garantit pas que la dernière coroutine soit
     * entièrement déroulée, mais réduit la fenêtre à presque rien — contre une
     * fenêtre de plusieurs secondes si l'on enchaînait reset puis REPLACE.
     */
    private suspend fun cancelRunningChain() {
        workManager.cancelUniqueWork(UNIQUE_CHAIN).await()
    }

    private companion object {
        /**
         * Un seul nom unique pour toutes les chaînes de workers : scans, ré-index
         * CLIP, ré-analyse visages. Avec des noms distincts, deux FaceAnalysisWorker
         * pouvaient tourner en même temps sur la même file `NOT EXISTS` et se
         * marcher dessus (visages en double via delete/insert entrelacés).
         */
        const val UNIQUE_CHAIN = "VINDEX_SCAN_PROCESS"
    }
}