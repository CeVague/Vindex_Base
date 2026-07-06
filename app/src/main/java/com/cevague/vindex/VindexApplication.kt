package com.cevague.vindex

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VindexApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsCache: SettingsCache

    override fun onCreate() {
        super.onCreate()
        // Applique le thème choisi dès le démarrage (avant toute activité).
        // La langue est persistée automatiquement par AndroidX (autoStoreLocales).
        AppCompatDelegate.setDefaultNightMode(
            when (settingsCache.themeMode) {
                Setting.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Setting.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
