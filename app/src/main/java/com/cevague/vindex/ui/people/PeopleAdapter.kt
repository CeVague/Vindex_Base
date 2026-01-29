package com.cevague.vindex.ui.people

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao.PersonWithCover
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.databinding.ItemPersonBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PeopleAdapter(
    private val repository: PersonRepository,
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
            binding.textName.text = person.name ?: binding.root.context.getString(R.string.people_unknown)

            binding.textCount.text = binding.root.resources.getQuantityString(
                R.plurals.people_photo_count,
                person.photoCount,
                person.photoCount
            )

            val faceData = FaceDao.FaceWithPhoto(
                id = person.id,
                filePath = person.coverPath,
                boxLeft = person.boxLeft,
                boxTop = person.boxTop,
                boxRight = person.boxRight,
                boxBottom = person.boxBottom
            )

            Glide.with(binding.imagePerson)
                .load(person.coverPath)
                .transform(FaceCenterCrop(faceData), CircleCrop())
                .override(240, 240)
                .into(binding.imagePerson)

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
            newItem: PersonWithCover): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: PersonWithCover,
            newItem: PersonWithCover): Boolean {
            return oldItem == newItem
        }
    }
}
