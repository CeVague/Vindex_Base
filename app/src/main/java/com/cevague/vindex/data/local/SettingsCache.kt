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

    /**
     * Affiche les inconnus masqués en fin de trombinoscope, grisés.
     *
     * C'est un réglage d'**affichage**, pas de debug : sans lui, masquer serait
     * irréversible faute de pouvoir jamais revoir ce qu'on a masqué.
     */
    var showHiddenPeople: Boolean
        get() = prefs.getBoolean(Setting.KEY_SHOW_HIDDEN_PEOPLE, DEFAULT_SHOW_HIDDEN_PEOPLE)
        set(value) = prefs.edit { putBoolean(Setting.KEY_SHOW_HIDDEN_PEOPLE, value) }

    /** Émet l'état courant puis chaque changement : le trombinoscope se recompose. */
    val showHiddenPeopleFlow: Flow<Boolean> = callbackFlow {
        trySend(showHiddenPeople)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Setting.KEY_SHOW_HIDDEN_PEOPLE) trySend(showHiddenPeople)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

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

    /**
     * Désactivé, l'analyse laisse **tous** les visages en attente d'identification :
     * le regroupement devient 100 % manuel, et le résultat est une **vérité terrain**.
     *
     * C'est le seul moyen de calibrer autrement qu'à l'œil : sans elle, on ne peut
     * que déduire les seuils de la *forme* d'une distribution, en supposant qui est
     * qui. Réservé au debug — sur une vraie galerie, nommer chaque visage à la main
     * est hors de question.
     */
    var autoClusteringEnabled: Boolean
        get() = prefs.getBoolean(Setting.KEY_AUTO_CLUSTERING, DEFAULT_AUTO_CLUSTERING)
        set(value) = prefs.edit { putBoolean(Setting.KEY_AUTO_CLUSTERING, value) }

    /**
     * Sous cette qualité (cf. `faceQuality`), un visage est écarté d'office.
     * 0 = ne rien écarter.
     */
    var faceQualityFloor: Float
        get() = prefs.getFloat(Setting.KEY_FACE_QUALITY_FLOOR, DEFAULT_FACE_QUALITY_FLOOR)
        set(value) = prefs.edit { putFloat(Setting.KEY_FACE_QUALITY_FLOOR, value) }

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
        const val DEFAULT_AUTO_CLUSTERING = true
        const val DEFAULT_SHOW_HIDDEN_PEOPLE = false

        /**
         * Plancher de qualité, calibré le 2026-07-16 sur 102 visages étiquetés à la
         * main, selon la règle de l'utilisateur : **~5 % de vrais visages perdus est
         * acceptable si plus de 15 % du rebut disparaît**.
         *
         * À 0,18 : **1 seul vrai visage perdu sur 41** (2,4 %) pour **72 % du rebut
         * éliminé** — cinq fois le gain demandé, pour la moitié du coût toléré. Et la
         * falaise est juste après : 0,21 coûte 7,3 % pour 5 points de gain de plus.
         *
         * ⚠ Le visage perdu est instructif : un Laurence minuscule et lointain, que
         * l'utilisateur **n'avait pas reconnu lui-même** à la première apparition. Un
         * visage qu'un humain ne peut pas identifier n'a rien à faire dans une
         * identité — d'où l'abandon d'un plancher « zéro perte » (0,02), qui protégeait
         * un visage sans valeur au prix de 16 points de rebut.
         *
         * Le rebut qui survit : les **dessins** (imperméables à la qualité, cf.
         * `faceQuality`) et 9 « rien à voir ». Ils restent manuels.
         */
        const val DEFAULT_FACE_QUALITY_FLOOR = 0.18f

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
        // (décision groupe↔groupe), pas à placer un visage. 0,32 **confirmé sur vérité
        // terrain** (2026-07-15) : regroupement 100 % manuel de 43 visages en 10
        // personnes, export de toutes les paires, une fois deux erreurs de clic
        // retirées. Sur 639 paires de personnes différentes, les DEUX seules qui
        // atteignent 0,32-0,326 sont deux fratries (P2/P4) — 0,326 est le plafond
        // absolu des « différents ». Le seuil est posé pile à cette frontière : un
        // cran plus bas, les frères et sœurs fusionnent.
        //
        // Il est plus BAS que HIGH, ce qui surprend puis s'explique : les groupes qui
        // se sont scindés sont justement les cas difficiles, ceux qu'un visage médiocre
        // a mal ancrés. Leurs centroïdes sont donc mauvais, et se ressemblent moins que
        // deux vues nettes d'une même personne.
        //
        // ⚠ Limite mesurée que NUL seuil ne comble : 19 % des paires d'une même
        // personne tombent SOUS ce plafond de 0,326 — profils, flou, visages lointains,
        // indiscernables d'inconnus par la seule similarité. C'est la motivation
        // chiffrée de la pondération par qualité + du max-sur-les-visages.
        const val DEFAULT_FACE_THRESHOLD_HIGH = 0.45f
        const val DEFAULT_FACE_THRESHOLD_MEDIUM = 0.40f
        const val DEFAULT_FACE_THRESHOLD_NEW = 0.32f
    }
}
