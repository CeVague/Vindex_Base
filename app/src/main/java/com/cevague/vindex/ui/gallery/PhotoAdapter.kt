package com.cevague.vindex.ui.gallery

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
import com.cevague.vindex.databinding.ItemGalleryHeaderBinding
import com.cevague.vindex.databinding.ItemGalleryPhotoBinding

class GalleryAdapter(
    private val targetSize: Int,
    private val onPhotoClick: (PhotoSummary, Int) -> Unit
) : ListAdapter<GalleryItem, RecyclerView.ViewHolder>(GalleryDiffCallback()) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_PHOTO = 1
    }

    class HeaderViewHolder(
        private val binding: ItemGalleryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: GalleryItem.Header) {
            binding.textHeader.text = header.title
        }
    }

    class PhotoViewHolder(
        private val binding: ItemGalleryPhotoBinding,
        private val onPhotoClick: (PhotoSummary, Int) -> Unit,
        val targetSize: Int
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentPhoto: PhotoSummary? = null

        init {
            binding.root.setOnClickListener {
                currentPhoto?.let { photo ->
                    onPhotoClick(photo, bindingAdapterPosition)
                }
            }
        }

        fun bind(item: GalleryItem.PhotoItem) {
            currentPhoto = item.photo

            Glide.with(binding.imagePhoto)
                .load(item.photo.filePath.toUri())
                .format(DecodeFormat.PREFER_RGB_565)
                .override(targetSize)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(binding.imagePhoto)
        }

        fun recycle() {
            Glide.with(binding.imagePhoto).clear(binding.imagePhoto)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GalleryItem.Header -> VIEW_TYPE_HEADER
            is GalleryItem.PhotoItem -> VIEW_TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemGalleryHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }

            VIEW_TYPE_PHOTO -> {
                val binding = ItemGalleryPhotoBinding.inflate(inflater, parent, false)
                PhotoViewHolder(binding, onPhotoClick, targetSize)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GalleryItem.Header -> (holder as HeaderViewHolder).bind(item)
            is GalleryItem.PhotoItem -> (holder as PhotoViewHolder).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is PhotoViewHolder) {
            holder.recycle()
        }
    }


    fun getPhotoIndex(adapterPosition: Int): Int {
        var photoIndex = 0
        for (i in 0 until adapterPosition) {
            if (getItem(i) is GalleryItem.PhotoItem) {
                photoIndex++
            }
        }
        return photoIndex
    }

    fun getPhotosOnly(): List<PhotoSummary> {
        return currentList
            .filterIsInstance<GalleryItem.PhotoItem>()  // Garde seulement les PhotoItem
            .map { it.photo }  // Extrait le PhotoSummary de chaque PhotoItem
    }
}


class GalleryDiffCallback : DiffUtil.ItemCallback<GalleryItem>() {

    override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
        return when {
            // Deux headers sont les mêmes s'ils ont le même ID
            oldItem is GalleryItem.Header && newItem is GalleryItem.Header -> {
                oldItem.id == newItem.id
            }
            // Deux photos sont les mêmes si elles ont le même ID
            oldItem is GalleryItem.PhotoItem && newItem is GalleryItem.PhotoItem -> {
                oldItem.photo.id == newItem.photo.id
            }
            // Un header et une photo ne sont jamais les mêmes
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
        // Les data classes comparent automatiquement tous les champs
        return oldItem == newItem
    }
}