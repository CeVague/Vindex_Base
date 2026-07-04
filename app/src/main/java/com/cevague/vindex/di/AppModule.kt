package com.cevague.vindex.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cevague.vindex.data.database.AppDatabase
import com.cevague.vindex.data.database.dao.AiModelDao
import com.cevague.vindex.data.database.dao.AlbumDao
import com.cevague.vindex.data.database.dao.AnalysisLogDao
import com.cevague.vindex.data.database.dao.CityDao
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.dao.PhotoAnalysisDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoHashDao
import com.cevague.vindex.ui.gallery.PhotoGrouper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // CRUCIAL
            // Phase pré-release (dev solo) : migrations destructives assumées, le schéma v1
            // est modifié sur place sans bump de version (l'app est réinstallée à chaque
            // changement). À retirer à la première release : migrations + tests obligatoires.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("fast_settings", Context.MODE_PRIVATE)
    }

    @Provides
    fun providePhotoDao(db: AppDatabase): PhotoDao = db.photoDao()

    @Provides
    fun providePhotoAnalysisDao(db: AppDatabase): PhotoAnalysisDao = db.photoAnalysisDao()

    @Provides
    fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()

    @Provides
    fun provideFaceDao(db: AppDatabase): FaceDao = db.faceDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideCityDao(db: AppDatabase): CityDao = db.cityDao()

    @Provides
    fun provideAiModelDao(db: AppDatabase): AiModelDao = db.aiModelDao()

    @Provides
    fun providePhotoHashDao(db: AppDatabase): PhotoHashDao = db.photoHashDao()

    @Provides
    fun provideAnalysisLogDao(db: AppDatabase): AnalysisLogDao = db.analysisLogDao()

    @Provides
    fun providePhotoGrouper(@ApplicationContext context: Context): PhotoGrouper {
        return PhotoGrouper(context)
    }

    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
