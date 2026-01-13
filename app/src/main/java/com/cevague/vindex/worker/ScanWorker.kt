package com.cevague.vindex.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.util.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class ScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 1. Récupérer l'URI du dossier à scanner
        val folderUriString = inputData.getString("FOLDER_URI") ?: return@withContext Result.failure()
        val folderUri = folderUriString.toUri()

        try {
            // 2. Utiliser MediaScanner pour lister les photos actuelles sur le disque
            val scanner = MediaScanner()
            val photosFound = scanner.scanFolder(applicationContext, folderUri)

            // 3. Récupérer le repository via l'Application
            val repository = (applicationContext as VindexApplication).photoRepository

            // 4. Synchroniser : cette méthode gère maintenant les ajouts,
            // les mises à jour et les suppressions en une seule passe.
            repository.syncPhotos(photosFound)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}