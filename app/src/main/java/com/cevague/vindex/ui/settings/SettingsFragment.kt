package com.cevague.vindex.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.data.repository.PhotoRepository
import com.cevague.vindex.util.ScanManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    @Inject lateinit var settingsCache: SettingsCache
    @Inject lateinit var scanManager: ScanManager
    @Inject lateinit var photoRepository: PhotoRepository
    @Inject lateinit var personRepository: PersonRepository
    @Inject lateinit var albumRepository: AlbumRepository

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Configurer le DataStore AVANT de charger les préférences
        preferenceManager.preferenceDataStore = CacheBasedDataStore()

        // Charger le XML des préférences
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Configurer les préférences individuelles
        setupStatisticsPreferences()
        setupDisplayPreferences()
        setupAiPreferences()
        setupDataPreferences()
        setupAboutPreferences()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observer les statistiques pour mettre à jour l'affichage
        observeStatistics()
    }

    private fun setupStatisticsPreferences() {
        // Les stats sont mises à jour via observeStatistics()
    }

    private fun setupDisplayPreferences() {
        // Thème
        findPreference<ListPreference>(Setting.KEY_THEME)?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                applyTheme(newValue as String)
                true
            }
        }

        // Langue
        findPreference<ListPreference>(Setting.KEY_LANGUAGE)?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                applyLanguage(newValue as String)
                true
            }
        }

        // Colonnes de la grille
        findPreference<SeekBarPreference>(Setting.KEY_GRID_COLUMNS)?.apply {
            // Le summary est automatiquement mis à jour par SeekBarPreference
        }
    }

    private fun setupAiPreferences() {
        // Les thresholds utilisent EditTextPreference avec le DataStore
        // Rien de spécial à configurer
    }

    private fun setupDataPreferences() {
        // Rescanner la galerie
        findPreference<Preference>("rescan_gallery")?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                Toast.makeText(requireContext(), R.string.gallery_scanning, Toast.LENGTH_SHORT).show()
                scanManager.startFullScan()
            }
            true
        }

        // Réinitialiser la base de données
        findPreference<Preference>("reset_database")?.setOnPreferenceClickListener {
            showResetDatabaseDialog()
            true
        }

        // Réinitialiser les paramètres
        findPreference<Preference>("reset_settings")?.setOnPreferenceClickListener {
            showResetSettingsDialog()
            true
        }
    }

    private fun setupAboutPreferences() {
        // Version
        findPreference<Preference>("app_version")?.apply {
            summary = "${viewModel.appVersion} (${viewModel.appVersionCode})"
        }

        // Code source (GitHub)
        findPreference<Preference>("source_code")?.setOnPreferenceClickListener {
            openUrl("https://github.com/CeVague/Vindex")
            true
        }

        // Licence
        findPreference<Preference>("license")?.setOnPreferenceClickListener {
            openUrl("https://www.gnu.org/licenses/gpl-3.0.html")
            true
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Observation des statistiques
    // ════════════════════════════════════════════════════════════════════════

    private fun observeStatistics() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observer les stats de la bibliothèque
                launch {
                    viewModel.libraryStats.collect { stats ->
                        updateStatsSummaries(stats)
                    }
                }

                // Observer l'espace de stockage
                launch {
                    viewModel.storageUsed.collect { bytes ->
                        findPreference<Preference>("stat_storage")?.summary = bytes.formatFileSize()
                    }
                }
            }
        }
    }

    private fun updateStatsSummaries(stats: SettingsViewModel.LibraryStats) {
        findPreference<Preference>("stat_photos")?.summary = buildString {
            append(stats.visiblePhotos.formatNumber())
            if (stats.hiddenPhotos > 0) {
                append(" (${stats.hiddenPhotos} ${getString(R.string.settings_stat_hidden)})")
            }
        }

        findPreference<Preference>("stat_people")?.summary = buildString {
            append(stats.namedPeople.formatNumber())
            append(" ${getString(R.string.settings_stat_named)}")
            if (stats.unnamedPeople > 0) {
                append(", ${stats.unnamedPeople} ${getString(R.string.settings_stat_unnamed)}")
            }
        }

        findPreference<Preference>("stat_faces")?.summary = when {
            stats.pendingFaces == 0 -> getString(R.string.settings_stat_all_identified)
            else -> resources.getQuantityString(
                R.plurals.people_to_identify_count,
                stats.pendingFaces,
                stats.pendingFaces
            )
        }

        findPreference<Preference>("stat_albums")?.summary = stats.totalAlbums.formatNumber()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Actions
    // ════════════════════════════════════════════════════════════════════════

    private fun applyTheme(theme: String) {
        val mode = when (theme) {
            Setting.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Setting.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyLanguage(language: String) {
        val localeList = if (language == Setting.LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun showResetDatabaseDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset_db_title)
            .setMessage(R.string.settings_reset_db_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                performResetDatabase()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performResetDatabase() {
        lifecycleScope.launch {
            photoRepository.deleteAll()
            personRepository.deleteAllPersons()
            personRepository.deleteAllFaces()
            albumRepository.deleteAll()
            settingsCache.lastScanTimestamp = 0L

            Toast.makeText(requireContext(), R.string.settings_reset_db_done, Toast.LENGTH_SHORT).show()
            scanManager.startFullScan()
        }
    }

    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset)
            .setMessage(R.string.settings_reset_confirm)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                performResetSettings()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performResetSettings() {
        settingsCache.resetToDefaults()

        // Recharger les préférences pour refléter les changements
        preferenceScreen.removeAll()
        setPreferencesFromResource(R.xml.root_preferences, null)
        setupStatisticsPreferences()
        setupDisplayPreferences()
        setupAiPreferences()
        setupDataPreferences()
        setupAboutPreferences()

        // Appliquer le thème par défaut
        applyTheme(Setting.THEME_SYSTEM)
        applyLanguage(Setting.LANGUAGE_SYSTEM)

        Toast.makeText(requireContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }


    private inner class CacheBasedDataStore : PreferenceDataStore() {

        override fun getString(key: String, defValue: String?): String? {
            return when (key) {
                Setting.KEY_THEME -> settingsCache.themeMode
                Setting.KEY_LANGUAGE -> settingsCache.userLanguage
                Setting.KEY_FACE_THRESHOLD_HIGH -> settingsCache.faceThresholdHigh.toString()
                Setting.KEY_FACE_THRESHOLD_MEDIUM -> settingsCache.faceThresholdMedium.toString()
                Setting.KEY_FACE_THRESHOLD_NEW -> settingsCache.faceThresholdNew.toString()
                else -> defValue
            }
        }

        override fun getInt(key: String, defValue: Int): Int {
            return when (key) {
                Setting.KEY_GRID_COLUMNS -> settingsCache.gridColumns
                else -> defValue
            }
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return when (key) {
                Setting.KEY_SHOW_SCORES -> settingsCache.showScores
                else -> defValue
            }
        }

        override fun putString(key: String, value: String?) {
            value ?: return

            // Mettre à jour le cache immédiatement pour une UI réactive
            when (key) {
                Setting.KEY_THEME -> settingsCache.themeMode = value
                Setting.KEY_LANGUAGE -> settingsCache.userLanguage = value
                Setting.KEY_FACE_THRESHOLD_HIGH -> {
                    value.toFloatOrNull()?.let { settingsCache.faceThresholdHigh = it }
                }
                Setting.KEY_FACE_THRESHOLD_MEDIUM -> {
                    value.toFloatOrNull()?.let { settingsCache.faceThresholdMedium = it }
                }
                Setting.KEY_FACE_THRESHOLD_NEW -> {
                    value.toFloatOrNull()?.let { settingsCache.faceThresholdNew = it }
                }
            }

            // Sauvegarder dans la DB de manière asynchrone
            viewModel.saveStringSetting(key, value)
        }

        override fun putInt(key: String, value: Int) {
            // Mettre à jour le cache immédiatement
            when (key) {
                Setting.KEY_GRID_COLUMNS -> settingsCache.gridColumns = value
            }

            // Sauvegarder dans la DB de manière asynchrone
            viewModel.saveIntSetting(key, value)
        }

        override fun putBoolean(key: String, value: Boolean) {
            // Mettre à jour le cache immédiatement
            when (key) {
                Setting.KEY_SHOW_SCORES -> settingsCache.showScores = value
            }

            // Sauvegarder dans la DB de manière asynchrone
            viewModel.saveBooleanSetting(key, value)
        }
    }
}