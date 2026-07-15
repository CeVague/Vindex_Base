package com.cevague.vindex.ui.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cevague.vindex.R
import com.cevague.vindex.databinding.ItemAlbumBinding
import com.cevague.vindex.databinding.ItemAlbumHeaderBinding

/** Grille Albums : en-têtes de section (Automatiques / Dossiers) + cartes d'album. */
class AlbumsAdapter(
    var targetSize: Int,
    private val onCardClick: (AlbumListItem.Card) -> Unit
) : ListAdapter<AlbumListItem, RecyclerView.ViewHolder>(DIFF) {

    class HeaderViewHolder(val binding: ItemAlbumHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    class CardViewHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AlbumListItem.Header -> VIEW_TYPE_HEADER
        is AlbumListItem.Card -> VIEW_TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemAlbumHeaderBinding.inflate(inflater, parent, false))
        } else {
            CardViewHolder(ItemAlbumBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AlbumListItem.Header -> (holder as HeaderViewHolder).binding.textHeader.text =
                item.title

            is AlbumListItem.Card -> bindCard(holder as CardViewHolder, item)
        }
    }

    private fun bindCard(holder: CardViewHolder, card: AlbumListItem.Card) {
        val context = holder.itemView.context
        holder.binding.textName.text = card.name
        holder.binding.textCount.text = context.resources.getQuantityString(
            R.plurals.gallery_photo_count, card.count, card.count
        )
        holder.binding.root.setOnClickListener { onCardClick(card) }

        Glide.with(holder.binding.imageCover)
            .load(card.coverUri?.toUri())
            .format(DecodeFormat.PREFER_RGB_565)
            .override(targetSize)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(holder.binding.imageCover)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is CardViewHolder) Glide.with(holder.binding.imageCover)
            .clear(holder.binding.imageCover)
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_CARD = 1

        private val DIFF = object : DiffUtil.ItemCallback<AlbumListItem>() {
            override fun areItemsTheSame(oldItem: AlbumListItem, newItem: AlbumListItem): Boolean =
                when {
                    oldItem is AlbumListItem.Header && newItem is AlbumListItem.Header ->
                        oldItem.title == newItem.title

                    oldItem is AlbumListItem.Card && newItem is AlbumListItem.Card ->
                        oldItem.key == newItem.key

                    else -> false
                }

            override fun areContentsTheSame(
                oldItem: AlbumListItem,
                newItem: AlbumListItem
            ): Boolean =
                oldItem == newItem
        }
    }
}
