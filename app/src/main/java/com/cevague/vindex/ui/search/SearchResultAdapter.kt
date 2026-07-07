package com.cevague.vindex.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.databinding.ItemGalleryPhotoBinding
import java.util.Locale

/**
 * Grille de résultats de recherche. Simple [ListAdapter] : les résultats sont une
 * liste bornée en mémoire, la pagination de la galerie n'a pas lieu d'être ici.
 *
 * Le score de similarité fait partie de l'item ([ScoredPhoto]) et non d'un état
 * externe : le DiffUtil re-lie ainsi les vignettes dont seul le score a changé
 * entre deux recherches.
 */
class SearchResultAdapter(
    var targetSize: Int,
    private val onClick: (PhotoSummary) -> Unit
) : ListAdapter<SearchResultAdapter.ScoredPhoto, SearchResultAdapter.PhotoViewHolder>(DIFF) {

    data class ScoredPhoto(val photo: PhotoSummary, val score: Float?)

    /** [scores] n'est affiché que si [showScores] (réglage « Afficher les scores »). */
    fun submitPhotos(
        photos: List<PhotoSummary>,
        scores: Map<Long, Float> = emptyMap(),
        showScores: Boolean = false,
        onCommitted: (() -> Unit)? = null
    ) {
        submitList(
            photos.map { ScoredPhoto(it, if (showScores) scores[it.id] else null) },
            onCommitted
        )
    }

    /** Photos affichées, dans l'ordre courant (ids pour la session du viewer). */
    val photos: List<PhotoSummary> get() = currentList.map { it.photo }

    inner class PhotoViewHolder(
        val binding: ItemGalleryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(
            ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.root.setOnClickListener { onClick(item.photo) }
        holder.binding.textScore.visibility = if (item.score != null) View.VISIBLE else View.GONE
        item.score?.let {
            holder.binding.textScore.text = String.format(Locale.US, "%.2f", it)
        }
        Glide.with(holder.binding.imagePhoto)
            .load(item.photo.filePath.toUri())
            .format(DecodeFormat.PREFER_RGB_565)
            .override(targetSize)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(holder.binding.imagePhoto)
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        Glide.with(holder.binding.imagePhoto).clear(holder.binding.imagePhoto)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScoredPhoto>() {
            override fun areItemsTheSame(oldItem: ScoredPhoto, newItem: ScoredPhoto) =
                oldItem.photo.id == newItem.photo.id

            override fun areContentsTheSame(oldItem: ScoredPhoto, newItem: ScoredPhoto) =
                oldItem == newItem
        }
    }
}
