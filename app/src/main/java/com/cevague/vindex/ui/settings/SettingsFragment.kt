package com.cevague.vindex.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.FastSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.cevague.vindex.R
import android.net.Uri

class SettingsFragment : PreferenceFragmentCompat() {

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 1. Persister la permission (important pour que l'app y accède après un redémarrage)
            requireContext().contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // 2. Sauvegarder dans ton repository
            val repository = (requireActivity().application as VindexApplication).settingsRepository
            lifecycleScope.launch {
                repository.setSourceFolderUri(it.toString())
                // Optionnel : Mettre à jour l'UI du fragment immédiatement
                findPreference<Preference>(Setting.KEY_SOURCE_FOLDER_URI)?.summary = it.toString()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val repository = (requireActivity().application as VindexApplication).settingsRepository

        findPreference<Preference>(Setting.KEY_SOURCE_FOLDER_URI)?.setOnPreferenceClickListener {
            folderPickerLauncher.launch(null) // Ouvre le sélecteur
            true
        }

        preferenceManager.preferenceDataStore = object : PreferenceDataStore() {

            override fun putString(key: String, value: String?) {
                lifecycleScope.launch {
                    value?.let {
                        repository.setValue(key, it)
                        // MISE À JOUR DES MIROIRS CRITIQUES
                        when (key) {
                            Setting.KEY_SOURCE_FOLDER_URI -> FastSettings.sourceFolderUri = it
                            Setting.KEY_THEME -> FastSettings.themeMode = it
                            Setting.KEY_LANGUAGE -> FastSettings.userLanguage = it
                        }
                    }
                }
            }

            override fun putBoolean(key: String, value: Boolean) {
                lifecycleScope.launch { repository.setValue(key, value.toString()) }
            }

            override fun putInt(key: String, value: Int) {
                lifecycleScope.launch { repository.setValue(key, value.toString()) }
            }

            // LECTURE (On tente le miroir d'abord pour la fluidité)
            override fun getString(key: String, defaultValue: String?): String? {
                return when (key) {
                    Setting.KEY_SOURCE_FOLDER_URI -> FastSettings.sourceFolderUri
                    Setting.KEY_THEME -> FastSettings.themeMode
                    Setting.KEY_LANGUAGE -> FastSettings.userLanguage
                    else -> runBlocking { repository.getValueOnce(key) } ?: defaultValue
                }
            }

            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                val value = runBlocking { repository.getValueOnce(key) }
                return value?.toBoolean() ?: defaultValue
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                val value = runBlocking { repository.getValueOnce(key) }
                return value?.toIntOrNull() ?: defaultValue
            }
        }

        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}