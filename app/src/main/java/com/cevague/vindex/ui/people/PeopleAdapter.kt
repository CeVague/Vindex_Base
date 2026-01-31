package com.cevague.vindex.ui.people

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.signature.ObjectKey
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao.PersonWithCover
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.ItemPersonBinding
import kotlinx.coroutines.Job

class PeopleAdapter(
    private val settingsCache: SettingsCache,
    private val onPersonClick: (PersonWithCover) -> Unit,
    private val onPersonLongClick: (PersonWithCover, View) -> Unit
) : ListAdapter<PersonWithCover, PeopleAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPersonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var loadJob: Job? = null


        fun bind(person: PersonWithCover) {
            binding.textName.text =
                person.name ?: binding.root.context.getString(R.string.people_unknown)

            binding.textCount.text = binding.root.resources.getQuantityString(
                R.plurals.people_photo_count,
                person.photoCount,
                person.photoCount
            )

            val coverPath = person.coverPath

            if (coverPath != null) {
                val faceData = FaceDao.FaceWithPhoto(
                    id = person.id,
                    filePath = coverPath,
                    boxLeft = person.boxLeft ?: 0f,
                    boxTop = person.boxTop ?: 0f,
                    boxRight = person.boxRight ?: 0f,
                    boxBottom = person.boxBottom ?: 0f
                )

                val spanCount = settingsCache.gridColumns
                val screenWidth = binding.root.context.resources.displayMetrics.widthPixels
                val targetSize = screenWidth / spanCount

                Glide.with(binding.imagePerson)
                    .load(coverPath)
                    .signature(ObjectKey(person.id.toString() + person.photoCount))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(targetSize)
                    .placeholder(R.drawable.vector_peoples)
                    .error(R.drawable.vector_peoples)
                    .transform(FaceCropTransformation(faceData), CircleCrop())
                    .into(binding.imagePerson)
            } else {
                Glide.with(binding.imagePerson)
                    .load(R.drawable.vector_peoples)
                    .transform(CircleCrop())
                    .into(binding.imagePerson)
            }

            binding.root.setOnClickListener { onPersonClick(person) }

            binding.root.setOnLongClickListener {
                onPersonLongClick(person, binding.root)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PersonWithCover>() {
        override fun areItemsTheSame(
            oldItem: PersonWithCover,
            newItem: PersonWithCover
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: PersonWithCover,
            newItem: PersonWithCover
        ): Boolean {
            return oldItem == newItem
        }
    }
}
