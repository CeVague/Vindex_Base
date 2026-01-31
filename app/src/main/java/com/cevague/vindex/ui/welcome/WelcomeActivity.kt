package com.cevague.vindex.ui.welcome

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.ActivityWelcomeBinding
import com.cevague.vindex.ui.main.MainActivity
import com.cevague.vindex.util.MediaScanner
import com.cevague.vindex.util.ScanManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private var availableFolders: List<MediaScanner.FolderInfo> = emptyList()
    private val selectedFolders = mutableSetOf<String>()

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

            availableFolders = mediaScanner.listImageFolders(this@WelcomeActivity)

            //binding.progressBar.visibility = View.GONE

            showFolderPickerDialog()
        }
    }

    private fun showFolderPickerDialog() {
        val folderNames =
            availableFolders.map { "${it.relativePath} (${it.photoCount})" }.toTypedArray()
        val checkedItems = BooleanArray(availableFolders.size) {
            // Pré-sélectionner DCIM et Pictures
            availableFolders[it].relativePath.startsWith("DCIM") ||
                    availableFolders[it].relativePath.startsWith("Pictures")
        }

        // Initialiser selectedFolders avec les pré-sélectionnés
        availableFolders.forEachIndexed { index, folder ->
            if (checkedItems[index]) selectedFolders.add(folder.relativePath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Sélectionner les dossiers à scanner")
            .setMultiChoiceItems(folderNames, checkedItems) { _, which, isChecked ->
                val folder = availableFolders[which].relativePath
                if (isChecked) {
                    selectedFolders.add(folder)
                } else {
                    selectedFolders.remove(folder)
                }
            }
            .setPositiveButton("Valider") { _, _ ->
                if (selectedFolders.isNotEmpty()) {
                    saveAndContinue()
                } else {
                    Toast.makeText(this, "Sélectionnez au moins un dossier", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saveAndContinue() {

        lifecycleScope.launch {
            // Sauvegarder les dossiers sélectionnés
            settingsCache.includedFolders = selectedFolders
            settingsCache.isConfigured = true

            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))

            // Lancer le scan
            scanManager.startFullScan()

            finish()
        }
    }
}