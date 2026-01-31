package com.cevague.vindex.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cevague.vindex.worker.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun startGalleryScan() {
        val discoveryReq = OneTimeWorkRequestBuilder<DiscoveryWorker>()
            .addTag("SCAN_TAG")
            .build()

        val metadataReq = OneTimeWorkRequestBuilder<MetadataWorker>()
            .addTag("SCAN_TAG")
            .build()

        workManager
            .beginUniqueWork("VINDEX_SCAN_PROCESS", ExistingWorkPolicy.KEEP, discoveryReq)
            .then(metadataReq)
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

        val aiReq = OneTimeWorkRequestBuilder<AIAnalysisWorker>()
            .addTag("SCAN_TAG")
            .build()

        val faceReq = OneTimeWorkRequestBuilder<FaceAnalysisWorker>()
            .addTag("SCAN_TAG")
            .build()

        workManager
            .beginUniqueWork("VINDEX_SCAN_PROCESS", ExistingWorkPolicy.KEEP, discoveryReq)
            .then(citiesReq)
            .then(metadataReq)
            .then(aiReq)
            .then(faceReq)
            .enqueue()
    }

    fun cancelAllScans() {
        workManager.cancelAllWorkByTag("SCAN_TAG")
    }
}