package com.cevague.vindex.ui.people

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.Person
import com.cevague.vindex.data.repository.PersonRepository
import com.cevague.vindex.databinding.ItemPersonBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PeopleAdapter(
    private val repository: PersonRepository,
    private val onPersonClick: (Person) -> Unit
) : ListAdapter<Person, PeopleAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(person: Person) {
            loadJob?.cancel()
            binding.textName.text =
                person.name ?: binding.root.context.getString(R.string.people_unknown)
            val countText = if (person.photoCount <= 1) {
                binding.root.context.getString(R.string.gallery_photo_count_singular)
            } else {
                binding.root.context.getString(R.string.gallery_photo_count, person.photoCount)
            }
            binding.textCount.text = countText
            binding.imagePerson.setImageResource(R.drawable.vector_peoples)

            binding.root.post {
                val lifecycleOwner = binding.root.findViewTreeLifecycleOwner()
                loadJob = lifecycleOwner?.lifecycleScope?.launch {
                    // On récupère les coordonnées du visage
                    val faceData = repository.getPrimaryFaceWithPhoto(person.id)

                    if (faceData != null) {
                        Glide.with(binding.imagePerson)
                            .load(faceData.filePath)
                            .transform(FaceCenterCrop(faceData), CircleCrop())
                            .override(240, 240) // Taille fixe pour la RAM
                            .into(binding.imagePerson)
                    }
                }
            }

            binding.root.setOnClickListener { onPersonClick(person) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Person>() {
        override fun areItemsTheSame(oldItem: Person, newItem: Person) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Person, newItem: Person) = oldItem == newItem
    }
}