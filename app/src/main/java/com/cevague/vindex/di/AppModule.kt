package com.cevague.vindex.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cevague.vindex.data.database.AppDatabase
import com.cevague.vindex.data.database.dao.AiModelDao
import com.cevague.vindex.data.database.dao.AlbumDao
import com.cevague.vindex.data.database.dao.AnalysisLogDao
import com.cevague.vindex.data.database.dao.CityDao
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoHashDao
import com.cevague.vindex.data.database.dao.SettingDao
import com.cevague.vindex.data.database.entity.Setting
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
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val now = System.currentTimeMillis()

                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_FIRST_RUN}', 'true', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_INCLUDED_FOLDERS}', '', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_GRID_COLUMNS}', '3', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_THEME}', '${Setting.THEME_SYSTEM}', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_LANGUAGE}', '${Setting.LANGUAGE_SYSTEM}', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_SHOW_SCORES}', 'false', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_FACE_THRESHOLD_HIGH}', '0.40', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_FACE_THRESHOLD_MEDIUM}', '0.60', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_FACE_THRESHOLD_NEW}', '0.75', $now)")
                    db.execSQL("INSERT INTO settings (key, value, updated_at) VALUES ('${Setting.KEY_LAST_SCAN_TIMESTAMP}', '0', $now)")
                }
            })
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // CRUCIAL
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
    fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()

    @Provides
    fun provideFaceDao(db: AppDatabase): FaceDao = db.faceDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideSettingDao(db: AppDatabase): SettingDao = db.settingDao()

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
