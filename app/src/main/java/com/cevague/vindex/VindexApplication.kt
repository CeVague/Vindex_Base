package com.cevague.vindex

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cevague.vindex.data.database.AppDatabase
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.SettingsRepository
import com.cevague.vindex.workers.ScanWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Observer le passage au premier plan
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                triggerSync()
            }
        })
    }

    private fun triggerSync() {
        applicationScope.launch {
            val uri = settingsRepository.getSourceFolderUriOnce()
            if (uri != null) {
                val workRequest = OneTimeWorkRequestBuilder<ScanWorker>()
                    .setInputData(workDataOf("FOLDER_URI" to uri.toString()))
                    .build()
                // On utilise KEEP pour ne pas relancer si un scan est déjà en cours
                WorkManager.getInstance(this@VindexApplication).enqueue(workRequest)
            }
        }
    }

    companion object {
        lateinit var instance: VindexApplication
            private set
    }
}
