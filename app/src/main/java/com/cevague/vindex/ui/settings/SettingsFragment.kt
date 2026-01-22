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
                }
                true
            }

        preferenceManager.preferenceDataStore = object : PreferenceDataStore() {

            override fun putBoolean(key: String, value: Boolean) {
                lifecycleScope.launch { repository.setValue(key, value.toString()) }
            }

            override fun putInt(key: String, value: Int) {
                lifecycleScope.launch { repository.setValue(key, value.toString()) }
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