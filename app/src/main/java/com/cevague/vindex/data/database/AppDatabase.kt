package com.cevague.vindex.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cevague.vindex.data.database.dao.AiModelDao
import com.cevague.vindex.data.database.dao.AlbumDao
import com.cevague.vindex.data.database.dao.AnalysisLogDao
import com.cevague.vindex.data.database.dao.CityDao
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoHashDao
import com.cevague.vindex.data.database.dao.SettingDao
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.database.entity.Album
import com.cevague.vindex.data.database.entity.AlbumPhoto
import com.cevague.vindex.data.database.entity.AnalysisLog
import com.cevague.vindex.data.database.entity.City
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.database.entity.PhotoHash
import com.cevague.vindex.data.database.entity.Setting

@Database(
    entities = [
        Photo::class,
        Person::class,
        Face::class,
        Album::class,
        AlbumPhoto::class,
        AiModel::class,
        Setting::class,
        PhotoHash::class,
        City::class,
        AnalysisLog::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // DAOs
    abstract fun photoDao(): PhotoDao
    abstract fun personDao(): PersonDao
    abstract fun faceDao(): FaceDao
    abstract fun albumDao(): AlbumDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun settingDao(): SettingDao
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun cityDao(): CityDao
    abstract fun analysisLogDao(): AnalysisLogDao

    companion object {
        private const val DATABASE_NAME = "vindex_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private class DatabaseCallback : Callback() {
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
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
