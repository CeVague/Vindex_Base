package com.cevague.vindex.ui.albums

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cevague.vindex.R
import com.cevague.vindex.data.repository.AlbumRepository
import com.cevague.vindex.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    albumRepository: AlbumRepository,
    photoRepository: PhotoRepository
) : ViewModel() {

    val items: StateFlow<List<AlbumListItem>> = combine(
        albumRepository.getAutoAlbumCards(),
        photoRepository.getFolderAlbums()
    ) { autoCards, folders ->
        buildList {
            if (autoCards.isNotEmpty()) {
                add(AlbumListItem.Header(context.getString(R.string.albums_auto_albums)))
                autoCards.forEach {
                    add(
                        AlbumListItem.Card(
                            key = "album:${it.albumId}",
                            name = it.name,
                            coverUri = it.coverUri,
                            count = it.photoCount,
                            target = AlbumListItem.Card.Target.Album(it.albumId)
                        )
                    )
                }
            }
            if (folders.isNotEmpty()) {
                add(AlbumListItem.Header(context.getString(R.string.albums_folder_albums)))
                folders.forEach {
                    add(
                        AlbumListItem.Card(
                            key = "folder:${it.folderPath}",
                            name = it.folderPath.substringAfterLast('/').ifEmpty { it.folderPath },
                            coverUri = it.coverUri,
                            count = it.photoCount,
                            target = AlbumListItem.Card.Target.Folder(it.folderPath)
                        )
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
