package com.cevague.vindex.ui.search

import android.view.LayoutInflater
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

/**
 * Grille de résultats de recherche. Simple [ListAdapter] : les résultats sont une
 * liste bornée en mémoire, la pagination de la galerie n'a pas lieu d'être ici.
 */
class SearchResultAdapter(
    var targetSize: Int,
    private val onClick: (PhotoSummary) -> Unit
) : ListAdapter<PhotoSummary, SearchResultAdapter.PhotoViewHolder>(DIFF) {

    inner class PhotoViewHolder(
        val binding: ItemGalleryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(
            ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = getItem(position)
        holder.binding.root.setOnClickListener { onClick(photo) }
        Glide.with(holder.binding.imagePhoto)
            .load(photo.filePath.toUri())
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
        private val DIFF = object : DiffUtil.ItemCallback<PhotoSummary>() {
            override fun areItemsTheSame(oldItem: PhotoSummary, newItem: PhotoSummary) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PhotoSummary, newItem: PhotoSummary) =
                oldItem == newItem
        }
    }
}
