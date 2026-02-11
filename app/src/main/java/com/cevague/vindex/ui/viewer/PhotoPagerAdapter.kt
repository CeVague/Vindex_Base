package com.cevague.vindex.ui.viewer

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.github.chrisbanes.photoview.PhotoView

class PhotoPagerAdapter :
    ListAdapter<PhotoSummary, PhotoPagerAdapter.PhotoPageViewHolder>(DIFF_CALLBACK) {

    // Callback pour le tap simple (afficher/masquer UI)
    var onPhotoTap: (() -> Unit)? = null

    class PhotoPageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoPageViewHolder {
        // Créer une PhotoView programmatiquement
        val photoView = PhotoView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setOnViewTapListener { _, _, _ -> onPhotoTap?.invoke() }
        }
        return PhotoPageViewHolder(photoView)
    }

    override fun onBindViewHolder(holder: PhotoPageViewHolder, position: Int) {
        val photo = getItem(position)

        Glide.with(holder.itemView.context)
            .load(photo.filePath.toUri())
            .priority(Priority.IMMEDIATE)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            //.skipMemoryCache(true)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .fitCenter()
            .into(holder.photoView)
    }

    override fun onViewRecycled(holder: PhotoPageViewHolder) {
        Glide.with(holder.photoView).clear(holder.photoView)
        super.onViewRecycled(holder)
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