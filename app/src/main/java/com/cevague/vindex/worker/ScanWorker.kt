package com.cevague.vindex.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.workDataOf
import com.cevague.vindex.R

class ScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 1. Récupérer l'URI du dossier à scanner
        val folderUriString = inputData.getString("FOLDER_URI") ?: return@withContext Result.failure()
        val folderUri = folderUriString.toUri()

        try {
            // 2. Utiliser MediaScanner pour lister rapidement les photos sur le disque (sans exif)
            val scanner = MediaScanner()
            val photosShallowFound = scanner.scanFolderShallow(applicationContext, folderUri)

            // 3. Récupérer le repository via l'Application
            val repository = (applicationContext as VindexApplication).photoRepository

            // 4. Synchroniser : cette méthode gère maintenant les ajouts,
            // les mises à jour et les suppressions en une seule passe.
            repository.syncPhotos(photosShallowFound)

            // 5. Reanalyser toutes les photos qui en ont besoin par paquets
            val photosNeedingMetadataExtraction = repository.getPhotosNeedingMetadataExtraction()

            val total = photosNeedingMetadataExtraction.size
            val batchSize = 1

            photosNeedingMetadataExtraction.chunked(batchSize).forEachIndexed { index, batch ->
                val enrichedBatch = batch.mapNotNull { photo ->
                    // On récupère le fichier physique pour lire l'EXIF
                    val documentFile = DocumentFile.fromSingleUri(applicationContext, photo.filePath.toUri())
                    if (documentFile != null && documentFile.exists()) {
                        // On extrait les métadonnées réelles et on injecte l'ID original pour le REPLACE
                        scanner.createPhotoFromFile(applicationContext, documentFile, true).copy(id = photo.id)
                    } else {
                        null
                    }
                }

                if (enrichedBatch.isNotEmpty()) {
                    repository.insertAll(enrichedBatch)
                }

                // Mise à jour de la progression (0 à 100)
                val processedCount = (index + 1) * batchSize
                val progress = if (processedCount >= total) 100 else (processedCount * 100 / total)
                setProgress(workDataOf("PROGRESS" to progress))
                setProgress(workDataOf("WORK" to applicationContext.getString(R.string.progress_exif)))
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}