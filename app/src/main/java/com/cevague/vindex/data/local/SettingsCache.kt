package com.cevague.vindex.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.cevague.vindex.data.database.entity.Setting
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsCache @Inject constructor(
    private val prefs: SharedPreferences
) {
    // ════════════════════════════════════════════════════════════════════════
    // Configuration de l'application
    // ════════════════════════════════════════════════════════════════════════

    var isFirstRun: Boolean
        get() = prefs.getBoolean(Setting.KEY_FIRST_RUN, true)
        set(value) = prefs.edit { putBoolean(Setting.KEY_FIRST_RUN, value) }

    var isConfigured: Boolean
        get() = prefs.getBoolean(Setting.KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit { putBoolean(Setting.KEY_IS_CONFIGURED, value) }

    var isCitiesLoaded: Boolean
        get() = prefs.getBoolean(Setting.KEY_CITIES_LOADED, false)
        set(value) = prefs.edit { putBoolean(Setting.KEY_CITIES_LOADED, value) }

    /**
     * Asset de villes déjà importé, pour détecter qu'il a changé.
     *
     * `isCitiesLoaded` ne suffit pas : les SharedPreferences **survivent** à une
     * migration destructive de Room. Après un changement d'asset, le drapeau resterait
     * donc à `true` sur une base vidée — l'import serait sauté et la table `cities`
     * resterait vide, sans que rien ne le dise, avec pour seul symptôme une recherche
     * géographique qui ne trouve plus rien.
     */
    var citiesAssetLoaded: String
        get() = prefs.getString(Setting.KEY_CITIES_ASSET, "") ?: ""
        set(value) = prefs.edit { putString(Setting.KEY_CITIES_ASSET, value) }

    // ════════════════════════════════════════════════════════════════════════
    // Dossiers source
    // ════════════════════════════════════════════════════════════════════════

    var includedFolders: Set<String>
        get() = prefs.getStringSet(Setting.KEY_INCLUDED_FOLDERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(Setting.KEY_INCLUDED_FOLDERS, value) }

    var lastScanTimestamp: Long
        get() = prefs.getLong(Setting.KEY_LAST_SCAN_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(Setting.KEY_LAST_SCAN_TIMESTAMP, value) }

    // ════════════════════════════════════════════════════════════════════════
    // Apparence
    // ════════════════════════════════════════════════════════════════════════

    var themeMode: String
        get() = prefs.getString(Setting.KEY_THEME, Setting.THEME_SYSTEM) ?: Setting.THEME_SYSTEM
        set(value) = prefs.edit { putString(Setting.KEY_THEME, value) }

    var userLanguage: String
        get() = prefs.getString(Setting.KEY_LANGUAGE, Setting.LANGUAGE_SYSTEM)
            ?: Setting.LANGUAGE_SYSTEM
        set(value) = prefs.edit { putString(Setting.KEY_LANGUAGE, value) }

    var gridColumns: Int
        get() = prefs.getInt(Setting.KEY_GRID_COLUMNS, Setting.DEFAULT_GRID_COLUMNS)
        set(value) = prefs.edit { putInt(Setting.KEY_GRID_COLUMNS, value) }

    /** Émet le nombre de colonnes courant puis chaque changement (grille réactive). */
    val gridColumnsFlow: Flow<Int> = callbackFlow {
        trySend(gridColumns)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Setting.KEY_GRID_COLUMNS) trySend(gridColumns)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // ════════════════════════════════════════════════════════════════════════
    // IA & Reconnaissance faciale
    // ════════════════════════════════════════════════════════════════════════

    var showScores: Boolean
        get() = prefs.getBoolean(Setting.KEY_SHOW_SCORES, DEFAULT_SHOW_SCORES)
        set(value) = prefs.edit { putBoolean(Setting.KEY_SHOW_SCORES, value) }

    /**
     * La saisie **brute** de l'EditTextPreference, telle que tapée. C'est elle qui
     * est stockée, et non le float : une saisie invalide doit rester visible dans
     * le champ plutôt que de disparaître, sinon le réglage semble s'effacer tout
     * seul.
     */
    var searchThresholdInput: String
        get() = prefs.getString(Setting.KEY_SEARCH_THRESHOLD, "") ?: ""
        set(value) = prefs.edit { putString(Setting.KEY_SEARCH_THRESHOLD, value) }

    /**
     * Override manuel du seuil de similarité de la recherche sémantique.
     * Vide ou invalide = null = mode auto (le seuil vient du config.json du modèle
     * actif, `similarity_floor`).
     */
    val searchThresholdOverride: Float?
        get() = searchThresholdInput.toFloatOrNull()

    var faceThresholdHigh: Float
        get() = prefs.getFloat(Setting.KEY_FACE_THRESHOLD_HIGH, DEFAULT_FACE_THRESHOLD_HIGH)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_THRESHOLD_HIGH, value) }

    var faceThresholdMedium: Float
        get() = prefs.getFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, DEFAULT_FACE_THRESHOLD_MEDIUM)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, value) }

    var faceThresholdNew: Float
        get() = prefs.getFloat(Setting.KEY_FACE_THRESHOLD_NEW, DEFAULT_FACE_THRESHOLD_NEW)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_THRESHOLD_NEW, value) }

    // ════════════════════════════════════════════════════════════════════════
    // Méthodes utilitaires
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Réinitialise tous les paramètres à leurs valeurs par défaut.
     */
    fun resetToDefaults() {
        prefs.edit {
            putString(Setting.KEY_THEME, Setting.THEME_SYSTEM)
            putString(Setting.KEY_LANGUAGE, Setting.LANGUAGE_SYSTEM)
            putInt(Setting.KEY_GRID_COLUMNS, Setting.DEFAULT_GRID_COLUMNS)
            putBoolean(Setting.KEY_SHOW_SCORES, DEFAULT_SHOW_SCORES)
            putString(Setting.KEY_SEARCH_THRESHOLD, "")
            putFloat(Setting.KEY_FACE_THRESHOLD_HIGH, DEFAULT_FACE_THRESHOLD_HIGH)
            putFloat(Setting.KEY_FACE_THRESHOLD_MEDIUM, DEFAULT_FACE_THRESHOLD_MEDIUM)
            putFloat(Setting.KEY_FACE_THRESHOLD_NEW, DEFAULT_FACE_THRESHOLD_NEW)
        }
    }

    companion object {
        const val DEFAULT_SHOW_SCORES = false

        // Similarités cosinus (produit scalaire de vecteurs L2), même convention que
        // la recherche : plus haut = plus proche.
        //
        // Re-mesurés le 2026-07-15 après le crop à résolution dynamique (51 visages,
        // log CALIBRATION) : la distribution est franchement bimodale — 36 valeurs
        // sous 0,354, UNE seule à 0,410, puis 14 à partir de 0,502. La bande
        // [0,42 ; 0,50] est vide, et c'est le plus grand trou de la distribution.
        //
        // Le crop a élargi ce trou : les ré-apparitions démarraient à 0,453 avant, à
        // 0,502 après, à plafond de bruit inchangé (0,353 → 0,354). Des visages nets
        // plutôt qu'agrandis se reconnaissent plus franchement.
        //
        // HIGH au milieu de la bande vide. MEDIUM à 0,40 et non 0,35 : à 0,35 il
        // passait SOUS le plafond du bruit (0,3526 et 0,3536 le dépassaient de deux
        // millièmes), donc il posait des questions sur des inconnus.
        //
        // NEW n'est pas sur le même axe : il sert à proposer la fusion de deux groupes
        // (décision groupe↔groupe), pas à placer un visage — jamais calibré, cf. la
        // carte de proposition en mode debug.
        const val DEFAULT_FACE_THRESHOLD_HIGH = 0.45f
        const val DEFAULT_FACE_THRESHOLD_MEDIUM = 0.40f
        const val DEFAULT_FACE_THRESHOLD_NEW = 0.40f
    }
}
