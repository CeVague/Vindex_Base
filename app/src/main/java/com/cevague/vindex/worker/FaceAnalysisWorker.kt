package com.cevague.vindex.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cevague.vindex.BuildConfig
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Face
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException

class FaceAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        return try {
            setProgress(
                workDataOf(
                    "WORK" to applicationContext.getString(R.string.progress_faces),
                    "PROGRESS" to 0
                )
            )

            // Estimation d'un batch selon le nombre de coeurs
            val cores = Runtime.getRuntime().availableProcessors()
            val batchSize = (cores * 5).coerceIn(5, 50)

            // Ajustement du batch pour les appareils Low RAM
            val activityManager =
                applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val isLowRamDevice = activityManager.isLowRamDevice
            // Si c'est un appareil Low RAM, on divise le batch par 2
            val finalBatchSize = if (isLowRamDevice) (batchSize / 2).coerceAtLeast(5) else batchSize

            if (BuildConfig.DEBUG) {
                delay(1000)


                // Dans FaceAnalysisWorker.kt

                val personRepo = (applicationContext as VindexApplication).personRepository
                val photoRepo = (applicationContext as VindexApplication).photoRepository

                // 1. Récupérer toutes les photos existantes
                val photos = photoRepo.getAllPhotosSummary().first()
                val existingPeople = personRepo.getAllPersonSummaryOnce()

                if (photos.isNotEmpty()) {
                    photos.take(20).forEach { photo ->
                        // Simulation : 30% de chances d'avoir des visages
                        if (Math.random() < 0.3) {
                            val faceCount = (1..3).random()
                            repeat(faceCount) {
                                val face = Face(
                                    photoId = photo.id,
                                    boxLeft = 0.1f,
                                    boxTop = 0.1f,
                                    boxRight = 0.4f,
                                    boxBottom = 0.4f,
                                    confidence = 0.95f,
                                    isPrimary = it == 0,
                                    assignmentType = "pending"
                                )
                                val faceId = personRepo.insertFace(face)

                                // 50% de chances de lier à une Personne
                                if (Math.random() < 0.5) {
                                    val personId =
                                        if (existingPeople.isNotEmpty() && Math.random() < 0.7) {
                                            existingPeople.random().id
                                        } else {
                                            personRepo.getOrCreatePersonByName(
                                                listOf(
                                                    "Alice",
                                                    "Bob",
                                                    "Charlie",
                                                    "David"
                                                ).random()
                                            )
                                        }

                                    personRepo.assignFaceToPerson(
                                        faceId = faceId,
                                        personId = personId,
                                        assignmentType = "manual",
                                        confidence = 1.0f,
                                        weight = 1.0f
                                    )
                                }
                            }
                        }
                    }
                }







                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_faces),
                        "PROGRESS" to 36
                    )
                )

                delay(1000)

                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_faces),
                        "PROGRESS" to 58
                    )
                )

                delay(1000)

                setProgress(
                    workDataOf(
                        "WORK" to applicationContext.getString(R.string.progress_faces),
                        "PROGRESS" to 89
                    )
                )

                delay(1000)
            }

            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: SQLiteException) {
            Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}