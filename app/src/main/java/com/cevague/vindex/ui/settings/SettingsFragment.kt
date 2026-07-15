package com.cevague.vindex.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.ui.common.FolderPickerDialog
import com.cevague.vindex.util.MediaScanner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var settingsCache: SettingsCache

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingsDataStore(settingsCache)
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        setupDisplayPreferences()
        setupAiPreferences()
        setupDataPreferences()
        setupAboutPreferences()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStatistics()
    }

    private fun setupDisplayPreferences() {
        findPreference<ListPreference>(Setting.KEY_THEME)?.setOnPreferenceChangeListener { _, newValue ->
            applyTheme(newValue as String)
            true
        }

        findPreference<ListPreference>(Setting.KEY_LANGUAGE)?.setOnPreferenceChangeListener { _, newValue ->
            applyLanguage(newValue as String)
            true
        }
    }

    private fun setupAiPreferences() {
        findPreference<Preference>("manage_models")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settings_to_models)
            true
        }

        findPreference<Preference>("advanced_settings")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settings_to_debug)
            true
        }
    }

    private fun setupDataPreferences() {
        findPreference<Preference>("source_folders")?.setOnPreferenceClickListener {
            editSourceFolders()
            true
        }

        findPreference<Preference>("rescan_gallery")?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), R.string.gallery_scanning, Toast.LENGTH_SHORT).show()
            viewModel.startFullScan()
            true
        }

        findPreference<Preference>("reset_database")?.setOnPreferenceClickListener {
            showResetDatabaseDialog()
            true
        }

        findPreference<Preference>("reset_settings")?.setOnPreferenceClickListener {
            showResetSettingsDialog()
            true
        }
    }

    private fun setupAboutPreferences() {
        findPreference<Preference>("app_version")?.summary =
            "${viewModel.appVersion} (${viewModel.appVersionCode})"
        findPreference<Preference>("source_code")?.setOnPreferenceClickListener { openUrl("https://github.com/CeVague/Vindex"); true }
        findPreference<Preference>("license")?.setOnPreferenceClickListener { openUrl("https://www.gnu.org/licenses/gpl-3.0.html"); true }
    }

    private fun observeStatistics() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.libraryStats.collect { stats -> updateStatsTiles(stats) }
                }
                launch {
                    viewModel.storage.collect { s ->
                        findPreference<StatsPreference>("stats_card")?.setStorage(
                            getString(
                                R.string.settings_storage_summary,
                                s.totalBytes.formatFileSize(),
                                s.modelsBytes.formatFileSize(),
                                s.databaseBytes.formatFileSize()
                            )
                        )
                    }
                }
            }
        }
    }

    /** Les nuances (masquées, sans nom) ne s'affichent que si elles existent. */
    private fun updateStatsTiles(stats: SettingsViewModel.LibraryStats) {
        findPreference<StatsPreference>("stats_card")?.setTiles(
            photos = StatsPreference.Tile(
                value = stats.visiblePhotos.formatNumber(),
                label = getString(R.string.settings_stat_photos),
                hint = stats.hiddenPhotos
                    .takeIf { it > 0 }
                    ?.let { "$it ${getString(R.string.settings_stat_hidden)}" }
            ),
            people = StatsPreference.Tile(
                value = stats.totalPeople.formatNumber(),
                label = getString(R.string.settings_stat_people),
                hint = stats.unnamedPeople
                    .takeIf { it > 0 }
                    ?.let { "$it ${getString(R.string.settings_stat_unnamed)}" }
            ),
            faces = StatsPreference.Tile(
                value = stats.pendingFaces.formatNumber(),
                label = getString(R.string.settings_stat_faces)
            ),
            albums = StatsPreference.Tile(
                value = stats.totalAlbums.formatNumber(),
                label = getString(R.string.settings_stat_albums)
            )
        )
    }

    private fun applyTheme(theme: String) {
        val mode = when (theme) {
            Setting.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Setting.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyLanguage(language: String) {
        val localeList =
            if (language == Setting.LANGUAGE_SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(
                language
            )
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun editSourceFolders() {
        viewLifecycleOwner.lifecycleScope.launch {
            val folders = viewModel.availableFolders()
            if (folders.isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val current = settingsCache.includedFolders
            FolderPickerDialog.show(requireContext(), folders, current) { selected ->
                val removed = current - selected
                when {
                    selected == current -> Unit // rien changé
                    removed.isNotEmpty() -> confirmFolderRemoval(removed, folders, selected)
                    else -> applyFolders(selected)
                }
            }
        }
    }

    private fun confirmFolderRemoval(
        removed: Set<String>,
        folders: List<MediaScanner.FolderInfo>,
        selected: Set<String>
    ) {
        val affected = folders.filter { it.relativePath in removed }.sumOf { it.photoCount }
        val list = removed.sorted().joinToString("\n") { "•  $it" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_folders_remove_title)
            .setMessage(getString(R.string.settings_folders_remove_message, list, affected))
            .setPositiveButton(R.string.action_remove) { _, _ -> applyFolders(selected) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun applyFolders(selected: Set<String>) {
        viewModel.applyIncludedFolders(selected)
        Toast.makeText(requireContext(), R.string.gallery_scanning, Toast.LENGTH_SHORT).show()
    }

    private fun showResetDatabaseDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset_db_title)
            .setMessage(R.string.settings_reset_db_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.resetDatabase()
                Toast.makeText(
                    requireContext(),
                    R.string.settings_reset_db_done,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset)
            .setMessage(R.string.settings_reset_confirm)
            .setPositiveButton(R.string.action_ok) { _, _ -> performResetSettings() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performResetSettings() {
        viewModel.resetSettings()
        applyTheme(Setting.THEME_SYSTEM)
        applyLanguage(Setting.LANGUAGE_SYSTEM)

        preferenceScreen.removeAll()
        addPreferencesFromResource(R.xml.root_preferences)
        setupDisplayPreferences()
        setupAiPreferences()
        setupDataPreferences()
        setupAboutPreferences()
        // L'écran vient d'être reconstruit : la carte de stats est neuve et vide
        // jusqu'à la prochaine émission des flows, qu'un reset ne déclenche pas.
        updateStatsTiles(viewModel.libraryStats.value)

        Toast.makeText(requireContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

}