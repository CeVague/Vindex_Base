package com.cevague.vindex

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cevague.vindex.data.database.AppDatabase
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.CityRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.SettingsRepository
import com.cevague.vindex.worker.AIAnalysisWorker
import com.cevague.vindex.worker.DiscoveryWorker
import com.cevague.vindex.worker.FaceAnalysisWorker
import com.cevague.vindex.worker.CityImportWorker
import com.cevague.vindex.worker.MetadataWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class VindexApplication : Application() {

    // Scope pour les tâches de fond de l'application
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Database instance
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    // Repositories
    val photoRepository: PhotoRepository by lazy {
        PhotoRepository(database.photoDao(), applicationScope)
    }

    val personRepository: PersonRepository by lazy {
        PersonRepository(database.personDao(), database.faceDao())
    }

    val albumRepository: AlbumRepository by lazy {
        AlbumRepository(database.albumDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.settingDao())
    }

    val cityRepository: CityRepository by lazy {
        CityRepository(database.cityDao())
    }


    override fun onCreate() {
        super.onCreate()
        instance = this
    }


    fun startGalleryScan() {
        val discoveryReq = OneTimeWorkRequestBuilder<DiscoveryWorker>()
            .addTag("SCAN_TAG")
            .build()

        val metadataReq = OneTimeWorkRequestBuilder<MetadataWorker>()
            .addTag("SCAN_TAG")
            .build()

        WorkManager.getInstance(this)
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


        WorkManager.getInstance(this)
            .beginUniqueWork("VINDEX_SCAN_PROCESS", ExistingWorkPolicy.KEEP, discoveryReq)
            .then(citiesReq)
            .then(metadataReq)
            .then(aiReq)
            .then(faceReq)
            .enqueue()
    }

    companion object {
        lateinit var instance: VindexApplication
            private set
    }
}
