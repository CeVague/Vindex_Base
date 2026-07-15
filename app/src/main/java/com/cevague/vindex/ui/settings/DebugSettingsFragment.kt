package com.cevague.vindex.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.cevague.vindex.R
import com.cevague.vindex.data.local.SettingsCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Réglages avancés : la calibration des seuils et l'affichage des scores.
 *
 * Écran séparé parce que ces six réglages ne se règlent pas, ils se **mesurent** —
 * et qu'ils demandent de taper des décimaux. Les laisser au milieu de Thème et
 * Langue donnait un écran principal aux deux tiers illisible.
 */
@AndroidEntryPoint
class DebugSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var settingsCache: SettingsCache

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingsDataStore(settingsCache)
        setPreferencesFromResource(R.xml.debug_preferences, rootKey)
    }
}
