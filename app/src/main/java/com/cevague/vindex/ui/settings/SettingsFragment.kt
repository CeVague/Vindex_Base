package com.cevague.vindex.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.FastSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.cevague.vindex.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val repository = (requireActivity().application as VindexApplication).settingsRepository

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