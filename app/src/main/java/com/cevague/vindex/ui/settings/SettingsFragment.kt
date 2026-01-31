package com.cevague.vindex.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.cevague.vindex.R
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.data.repository.SettingsRepository
import com.cevague.vindex.util.ScanManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var scanManager: ScanManager
    @Inject
    lateinit var settingsRepository: SettingsRepository
    @Inject
    lateinit var photoRepository: PhotoRepository
    @Inject
    lateinit var personRepository: PersonRepository
    @Inject
    lateinit var albumRepository: AlbumRepository
    @Inject
    lateinit var settingsCache: SettingsCache

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>("reset_database")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    photoRepository.deleteAll()
                    personRepository.deleteAllPersons()
                    personRepository.deleteAllFaces()
                    albumRepository.deleteAll()
                    settingsCache.lastScanTimestamp = 0L
                    Toast.makeText(requireContext(), "Database reset", Toast.LENGTH_SHORT).show()
                    scanManager.startFullScan()
                }
                true
            }

        preferenceManager.preferenceDataStore = object : PreferenceDataStore() {

            override fun putBoolean(key: String, value: Boolean) {
                lifecycleScope.launch { settingsRepository.setValue(key, value.toString()) }
            }

            override fun putInt(key: String, value: Int) {
                lifecycleScope.launch { settingsRepository.setValue(key, value.toString()) }
            }

            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                val value = runBlocking { settingsRepository.getValueOnce(key) }
                return value?.toBoolean() ?: defaultValue
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                val value = runBlocking { settingsRepository.getValueOnce(key) }
                return value?.toIntOrNull() ?: defaultValue
            }
        }
    }
}