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
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.databinding.ActivityWelcomeBinding
import com.cevague.vindex.ui.main.MainActivity
import com.cevague.vindex.worker.GeoNamesImportWorker
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

        WorkManager.getInstance(this)
            .beginUniqueWork(
                "GEONAMES_IMPORT", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GeoNamesImportWorker>().build()
            )
            .enqueue()
    }

    private fun handleFolderSelected(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val app = application as VindexApplication
        lifecycleScope.launch {
            app.settingsRepository.setSourceFolderUri(uri.toString())

            (application as VindexApplication).startFullScan(uri.toString())

            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
            finish()
        }
    }

}