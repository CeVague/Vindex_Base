package com.cevague.vindex.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.databinding.ItemPhotoBinding

class PhotoAdapter(private val onPhotoClick: (position: Int) -> Unit) :
    ListAdapter<PhotoSummary, PhotoAdapter.PhotoViewHolder>(DIFF_CALLBACK) {

    class PhotoViewHolder(binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        val imageView = binding.imageItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = getItem(position)

        Glide.with(holder.itemView.context)
            .load(photo.filePath.toUri())
            .centerCrop()
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onPhotoClick(position)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoSummary>() {
            override fun areItemsTheSame(oldItem: PhotoSummary, newItem: PhotoSummary): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PhotoSummary, newItem: PhotoSummary): Boolean {
                return oldItem == newItem
            }
        }
    }
}