package com.cevague.vindex.ui.settings

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.cevague.vindex.R
import com.cevague.vindex.data.local.SettingsCache
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Réglages avancés : la calibration des seuils et l'affichage des scores.
 *
 * Écran séparé parce que ces réglages ne se règlent pas, ils se **mesurent** — et
 * qu'ils demandent de taper des décimaux. Les laisser au milieu de Thème et Langue
 * donnait un écran principal aux deux tiers illisible.
 */
@AndroidEntryPoint
class DebugSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var settingsCache: SettingsCache

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingsDataStore(settingsCache)
        setPreferencesFromResource(R.xml.debug_preferences, rootKey)

        findPreference<Preference>("reanalyze_faces")?.setOnPreferenceClickListener {
            confirmFaceReanalysis()
            true
        }
    }

    /**
     * Le seul moyen de faire profiter les photos déjà analysées d'une amélioration
     * du pipeline : la file est un `NOT EXISTS` sur le composite des modèles, qui
     * ne bouge pas quand c'est le code qui change.
     */
    private fun confirmFaceReanalysis() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.models_reindex_faces_title)
            .setMessage(R.string.models_reindex_faces_message)
            .setPositiveButton(R.string.models_reindex_faces_action) { _, _ ->
                viewModel.startFaceReanalysis()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
