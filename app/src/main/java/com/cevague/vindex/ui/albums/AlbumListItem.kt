package com.cevague.vindex.ui.albums

/** Élément de la grille Albums : en-tête de section ou carte d'album. */
sealed interface AlbumListItem {

    data class Header(val title: String) : AlbumListItem

    data class Card(
        val key: String,
        val name: String,
        val coverUri: String?,
        val count: Int,
        val target: Target
    ) : AlbumListItem {

        /** Destination au clic : dossier virtuel (chemin) ou album matérialisé (id). */
        sealed interface Target {
            data class Folder(val folderPath: String) : Target
            data class Album(val albumId: Long) : Target
        }
    }
}
