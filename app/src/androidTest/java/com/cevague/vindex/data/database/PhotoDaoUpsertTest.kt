package com.cevague.vindex.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cevague.vindex.data.database.dao.PhotoAnalysisDao
import com.cevague.vindex.data.database.dao.PhotoDao
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.data.database.entity.PhotoAnalysis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Garantit l'item 1 de la dette phase 1 : `@Upsert` (et non REPLACE) préserve
 * l'id des photos modifiées et n'entraîne donc pas de suppression en cascade
 * des lignes filles (`photo_analyses`) lors d'un rescan.
 */
@RunWith(AndroidJUnit4::class)
class PhotoDaoUpsertTest {

    private lateinit var db: AppDatabase
    private lateinit var photoDao: PhotoDao
    private lateinit var analysisDao: PhotoAnalysisDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        photoDao = db.photoDao()
        analysisDao = db.photoAnalysisDao()
    }

    @After
    fun tearDown() = db.close()

    private fun photo(id: Long, fileSize: Long) = Photo(
        id = id,
        contentUri = "content://media/external/images/media/$id",
        fileName = "IMG_$id.jpg",
        fileLastModified = 100L,
        folderPath = "DCIM/Camera",
        dateAdded = 0L,
        fileSize = fileSize
    )

    @Test
    fun upsertPhotoModifiee_conserveIdEtLignesFilles() = runBlocking {
        photoDao.upsertAll(listOf(photo(id = 100, fileSize = 1_000L)))
        analysisDao.upsertAnalysis(
            PhotoAnalysis(
                photoId = 100,
                analysisType = PhotoAnalysis.TYPE_CAPTION,
                modelName = "test-model",
                textResult = "un chat"
            )
        )

        // Rescan : même photo (même id/URI) mais taille modifiée.
        photoDao.upsertAll(listOf(photo(id = 100, fileSize = 2_000L)))

        val stored = photoDao.getPhotoById(100).first()
        assertNotNull(stored)
        assertEquals(100L, stored!!.id)
        assertEquals(2_000L, stored.fileSize)

        // La ligne fille a survécu : un upsert met à jour en place, sans cascade.
        val analysis = analysisDao.getAnalysis(100, PhotoAnalysis.TYPE_CAPTION, "test-model")
        assertNotNull(analysis)
        assertEquals("un chat", analysis!!.textResult)
    }

    @Test
    fun suppressionPhoto_cascadeSurLesAnalyses() = runBlocking {
        photoDao.upsertAll(listOf(photo(id = 100, fileSize = 1_000L)))
        analysisDao.upsertAnalysis(
            PhotoAnalysis(
                photoId = 100,
                analysisType = PhotoAnalysis.TYPE_OCR,
                modelName = "test-model"
            )
        )

        photoDao.deleteByContentUris(listOf("content://media/external/images/media/100"))

        // La cascade FK est bien active : sans elle, le test ci-dessus n'aurait
        // aucune valeur (une ligne fille peut survivre par simple absence de FK).
        assertNull(analysisDao.getAnalysis(100, PhotoAnalysis.TYPE_OCR, "test-model"))
    }
}
