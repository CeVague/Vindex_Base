package com.cevague.vindex.ui.welcome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.databinding.ActivityWelcomeBinding
import com.cevague.vindex.ui.main.MainActivity
import com.cevague.vindex.workers.ScanWorker
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleFolderSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    private fun handleFolderSelected(uri: android.net.Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val app = application as VindexApplication
        lifecycleScope.launch {
            app.settingsRepository.setSourceFolderUri(uri.toString())

            startBackgroundScan(uri)

            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun startBackgroundScan(selectedUri: Uri) {
        // Préparer les données à envoyer au Worker
        val data = workDataOf("FOLDER_URI" to selectedUri.toString())

        // Créer la requête de travail
        val scanRequest = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(data)
            .addTag("media_scan")
            .build()

        // Lancer le travail de manière unique (évite de lancer 2 scans en même temps)
        WorkManager.getInstance(this).enqueueUniqueWork(
            "initial_folder_scan",
            ExistingWorkPolicy.REPLACE, // Remplace l'ancien scan s'il y en avait un
            scanRequest
        )
    }

}