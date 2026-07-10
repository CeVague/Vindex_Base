package com.cevague.vindex.ui.welcome

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.ActivityWelcomeBinding
import com.cevague.vindex.ui.common.FolderPickerDialog
import com.cevague.vindex.ui.main.MainActivity
import com.cevague.vindex.util.MediaScanner
import com.cevague.vindex.util.ScanManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private var availableFolders: List<MediaScanner.FolderInfo> = emptyList()

    @Inject
    lateinit var scanManager: ScanManager

    @Inject
    lateinit var mediaScanner: MediaScanner

    @Inject
    lateinit var settingsCache: SettingsCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectFolder.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadFoldersAndShowPicker()
        } else {
            // Afficher un message
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun loadFoldersAndShowPicker() {
        lifecycleScope.launch {
            // Afficher un loading
            //binding.progressBar.visibility = View.VISIBLE

            availableFolders = mediaScanner.listImageFolders()

            //binding.progressBar.visibility = View.GONE

            showFolderPickerDialog()
        }
    }

    private fun showFolderPickerDialog() {
        // Premier lancement : DCIM et Pictures pré-cochés par défaut.
        val preChecked = availableFolders
            .filter {
                it.relativePath.startsWith("DCIM") || it.relativePath.startsWith("Pictures")
            }
            .map { it.relativePath }
            .toSet()

        FolderPickerDialog.show(this, availableFolders, preChecked) { selected ->
            saveAndContinue(selected)
        }
    }

    private fun saveAndContinue(selected: Set<String>) {
        lifecycleScope.launch {
            settingsCache.includedFolders = selected
            settingsCache.isConfigured = true

            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))

            // Lancer le scan
            scanManager.startFullScan()

            finish()
        }
    }
}