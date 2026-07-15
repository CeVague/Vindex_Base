package com.cevague.vindex.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.cevague.vindex.R

/**
 * Les statistiques ne sont pas un réglage : elles ne se touchent pas, elles se
 * lisent. Elles tiennent donc en **une** carte de tuiles plutôt qu'en cinq lignes
 * de préférences non cliquables occupant tout le haut de l'écran.
 *
 * Chaque tuile garde sa nuance (masquées, sans nom, à identifier) en petit sous le
 * chiffre, et la masque quand elle est nulle : compacter ne doit pas coûter
 * d'information.
 */
class StatsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    data class Tile(val value: String, val label: String, val hint: String? = null)

    private var tiles: List<Tile> = emptyList()
    private var storage: String = ""

    init {
        layoutResource = R.layout.preference_stats
        isSelectable = false
    }

    /** Ordre imposé : photos, personnes, visages, albums (celui des tuiles du layout). */
    fun setTiles(photos: Tile, people: Tile, faces: Tile, albums: Tile) {
        tiles = listOf(photos, people, faces, albums)
        notifyChanged()
    }

    fun setStorage(text: String) {
        storage = text
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        TILE_IDS.forEachIndexed { index, id ->
            // Les quatre tuiles partagent les mêmes ids internes : la recherche est
            // donc faite depuis la racine de chaque tuile, jamais depuis le holder.
            holder.findViewById(id)?.let { bindTile(it, tiles.getOrNull(index)) }
        }

        (holder.findViewById(R.id.textStorage) as? TextView)?.apply {
            text = storage
            visibility = if (storage.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun bindTile(root: View, tile: Tile?) {
        root.findViewById<TextView>(R.id.textStatValue).text = tile?.value ?: PLACEHOLDER
        root.findViewById<TextView>(R.id.textStatLabel).text = tile?.label.orEmpty()
        root.findViewById<TextView>(R.id.textStatHint).apply {
            text = tile?.hint.orEmpty()
            visibility = if (tile?.hint.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private companion object {
        const val PLACEHOLDER = "—"
        val TILE_IDS = listOf(R.id.statPhotos, R.id.statPeople, R.id.statFaces, R.id.statAlbums)
    }
}
