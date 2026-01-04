package com.cevague.vindex

import android.app.Application
import com.cevague.vindex.data.database.AppDatabase
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.SettingsRepository

class VindexApplication : Application() {

    // Database instance
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    // Repositories
    val photoRepository: PhotoRepository by lazy { 
        PhotoRepository(database.photoDao()) 
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
    }

    companion object {
        lateinit var instance: VindexApplication
            private set
    }
}
