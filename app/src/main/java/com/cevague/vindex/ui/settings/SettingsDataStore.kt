package com.cevague.vindex.ui.settings

import android.util.Log
import androidx.preference.PreferenceDataStore
import com.cevague.vindex.data.database.entity.Setting
import com.cevague.vindex.data.local.SettingsCache

/**
 * Branche les écrans de préférences sur [SettingsCache] (SharedPreferences), seule
 * source de vérité des réglages depuis que la table `settings` a été supprimée.
 *
 * Partagé par tous les écrans de réglages : dès qu'un `PreferenceDataStore` est
 * installé, le framework **cesse complètement** de toucher aux SharedPreferences et
 * ne passe plus que par ici. Une clé oubliée n'est donc pas « moins bien gérée » :
 * elle est **morte**, en lecture comme en écriture, sans le moindre signe. C'est
 * exactement ce qui est arrivé à `search_threshold`, resté inerte de sa création
 * (2026-07-09) à sa découverte (2026-07-15) — d'où le `else` bruyant de chaque
 * `when` ci-dessous, et d'où le fait que cette classe soit unique plutôt que
 * recopiée par écran.
 */
class SettingsDataStore(private val settingsCache: SettingsCache) : PreferenceDataStore() {

    override fun getString(key: String, defValue: String?): String? = when (key) {
        Setting.KEY_THEME -> settingsCache.themeMode
        Setting.KEY_LANGUAGE -> settingsCache.userLanguage
        Setting.KEY_SEARCH_THRESHOLD -> settingsCache.searchThresholdInput
        Setting.KEY_FACE_THRESHOLD_HIGH -> settingsCache.faceThresholdHigh.toString()
        Setting.KEY_FACE_THRESHOLD_MEDIUM -> settingsCache.faceThresholdMedium.toString()
        Setting.KEY_FACE_THRESHOLD_NEW -> settingsCache.faceThresholdNew.toString()
        Setting.KEY_FACE_QUALITY_FLOOR -> settingsCache.faceQualityFloor.toString()
        else -> {
            Log.w(TAG, "Lecture d'une clé inconnue « $key » : valeur par défaut renvoyée")
            defValue
        }
    }

    override fun putString(key: String, value: String?) {
        val text = value ?: return
        when (key) {
            Setting.KEY_THEME -> settingsCache.themeMode = text
            Setting.KEY_LANGUAGE -> settingsCache.userLanguage = text
            Setting.KEY_SEARCH_THRESHOLD -> settingsCache.searchThresholdInput = text
            // Une saisie invalide est ignorée plutôt qu'écrite : le seuil resterait
            // sinon à zéro, et tout serait identifié comme tout le monde.
            Setting.KEY_FACE_THRESHOLD_HIGH -> text.toFloatOrNull()
                ?.let { settingsCache.faceThresholdHigh = it }

            Setting.KEY_FACE_THRESHOLD_MEDIUM -> text.toFloatOrNull()
                ?.let { settingsCache.faceThresholdMedium = it }

            Setting.KEY_FACE_THRESHOLD_NEW -> text.toFloatOrNull()
                ?.let { settingsCache.faceThresholdNew = it }

            Setting.KEY_FACE_QUALITY_FLOOR -> text.toFloatOrNull()
                ?.let { settingsCache.faceQualityFloor = it }

            else -> Log.w(TAG, "Réglage String non persisté : clé inconnue « $key »")
        }
    }

    override fun getInt(key: String, defValue: Int): Int = when (key) {
        Setting.KEY_GRID_COLUMNS -> settingsCache.gridColumns
        else -> {
            Log.w(TAG, "Lecture d'une clé inconnue « $key » : valeur par défaut renvoyée")
            defValue
        }
    }

    override fun putInt(key: String, value: Int) {
        when (key) {
            Setting.KEY_GRID_COLUMNS -> settingsCache.gridColumns = value
            else -> Log.w(TAG, "Réglage Int non persisté : clé inconnue « $key »")
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
        Setting.KEY_SHOW_SCORES -> settingsCache.showScores
        Setting.KEY_AUTO_CLUSTERING -> settingsCache.autoClusteringEnabled
        Setting.KEY_SHOW_HIDDEN_PEOPLE -> settingsCache.showHiddenPeople
        else -> {
            Log.w(TAG, "Lecture d'une clé inconnue « $key » : valeur par défaut renvoyée")
            defValue
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        when (key) {
            Setting.KEY_SHOW_SCORES -> settingsCache.showScores = value
            Setting.KEY_AUTO_CLUSTERING -> settingsCache.autoClusteringEnabled = value
            Setting.KEY_SHOW_HIDDEN_PEOPLE -> settingsCache.showHiddenPeople = value
            else -> Log.w(TAG, "Réglage Boolean non persisté : clé inconnue « $key »")
        }
    }

    private companion object {
        const val TAG = "SettingsDataStore"
    }
}
