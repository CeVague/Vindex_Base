package com.cevague.vindex.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.cevague.vindex.R
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.FastSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val app = requireActivity().application as VindexApplication
        val repository = app.settingsRepository

        findPreference<Preference>("reset_database")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    app.photoRepository.deleteAll()
                    app.personRepository.deleteAllPerson()
                    app.personRepository.deleteAllFaces()
                    app.albumRepository.deleteAll()
                    Toast.makeText(requireContext(), "Database reset", Toast.LENGTH_SHORT).show()
                    repository.getSourceFolderUriOnce()
                        ?.let { selectedUri -> app.startFullScan(selectedUri) }
                }
                true
            }

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
    }
}