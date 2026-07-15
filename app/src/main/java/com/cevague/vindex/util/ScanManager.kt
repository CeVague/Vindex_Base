package com.cevague.vindex.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cevague.vindex.data.database.entity.PhotoAnalysis
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
    private val photoRepository: PhotoRepository
) {
    private val workManager = WorkManager.getInstance(context)

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
            .beginUniqueWork("VINDEX_SCAN_PROCESS", ExistingWorkPolicy.KEEP, discoveryReq)
            .then(metadataReq)
            .then(clipReq)
            .then(faceReq)
            .then(cleanupReq)
            .enqueue()
    }

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
            .beginUniqueWork("VINDEX_SCAN_PROCESS", ExistingWorkPolicy.KEEP, discoveryReq)
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

    suspend fun startClipReindexing() {
        photoRepository.deleteAnalysesByType(PhotoAnalysis.TYPE_CLIP_EMBEDDING)
        val clipReq = OneTimeWorkRequestBuilder<ClipIndexWorker>()
            .addTag("SCAN_TAG")
            .build()
        workManager.enqueueUniqueWork("VINDEX_CLIP_INDEX", ExistingWorkPolicy.REPLACE, clipReq)
    }

    /**
     * Indexation initiale (premier modèle) : indexe les photos manquantes sans
     * rien supprimer. La file du worker est « photos sans vecteur du modèle actif »,
     * donc seuls les nouveaux éléments sont calculés.
     */
    fun startClipIndexing() {
        val clipReq = OneTimeWorkRequestBuilder<ClipIndexWorker>()
            .addTag("SCAN_TAG")
            .build()
        workManager.enqueueUniqueWork("VINDEX_CLIP_INDEX", ExistingWorkPolicy.KEEP, clipReq)
    }
}