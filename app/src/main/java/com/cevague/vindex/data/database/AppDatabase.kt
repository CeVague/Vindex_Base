package com.cevague.vindex.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cevague.vindex.data.database.dao.AiModelDao
import com.cevague.vindex.data.database.dao.AlbumDao
import com.cevague.vindex.data.database.dao.AnalysisLogDao
import com.cevague.vindex.data.database.dao.CityDao
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao
import com.cevague.vindex.data.database.dao.PhotoAnalysisDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.dao.PhotoHashDao
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.database.entity.Album
import com.cevague.vindex.data.database.entity.AlbumPhoto
import com.cevague.vindex.data.database.entity.AnalysisLog
import com.cevague.vindex.data.database.entity.City
import com.cevague.vindex.data.database.entity.Face
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import com.cevague.vindex.data.database.entity.PhotoHash

@Database(
    entities = [
        Photo::class,
        PhotoAnalysis::class,
        Person::class,
        Face::class,
        Album::class,
        AlbumPhoto::class,
        AiModel::class,
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
    abstract fun photoAnalysisDao(): PhotoAnalysisDao
    abstract fun personDao(): PersonDao
    abstract fun faceDao(): FaceDao
    abstract fun albumDao(): AlbumDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun cityDao(): CityDao
    abstract fun analysisLogDao(): AnalysisLogDao

    companion object {
        const val DATABASE_NAME = "vindex_database"
    }
}
