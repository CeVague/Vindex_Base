package com.cevague.vindex.ui.people

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.data.database.dao.PersonDao.PersonWithCover
import com.cevague.vindex.data.local.SettingsCache
import com.cevague.vindex.databinding.ItemPersonBinding
import kotlinx.coroutines.Job
import java.util.Locale

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
            // Grisé : personne nommée sans photo (dossier retiré), ou inconnu masqué
            // qu'on affiche exprès pour pouvoir le ré-afficher.
            binding.root.alpha = if (person.photoCount == 0 || person.isHidden) 0.4f else 1f

            binding.textName.text =
                person.name ?: binding.root.context.getString(R.string.people_unknown)

            binding.textCount.text = binding.root.resources.getQuantityString(
                R.plurals.people_photo_count,
                person.photoCount,
                person.photoCount
            )

            // Debug : meilleur score de détection du groupe (pas celui de la vignette
            // — cf. PersonWithCover.bestScore). C'est le nombre qui a servi à mesurer
            // la frontière animaux/humains sans re-détecter quoi que ce soit : monter
            // un seuil ne fait qu'enlever des visages, la base les contient donc déjà
            // tous. C'est aussi lui qui relègue les groupes douteux en fin de liste.
            val score = person.bestScore.takeIf { settingsCache.showScores }
            binding.textScore.visibility = if (score != null) View.VISIBLE else View.GONE
            if (score != null) {
                binding.textScore.text = String.format(Locale.US, "%.2f", score)
            }

            val coverPath = person.coverPath
            val coverFaceId = person.coverFaceId

            if (coverPath != null && coverFaceId != null) {
                val faceData = FaceDao.FaceWithPhoto(
                    id = coverFaceId,
                    filePath = coverPath,
                    boxLeft = person.boxLeft ?: 0f,
                    boxTop = person.boxTop ?: 0f,
                    boxRight = person.boxRight ?: 0f,
                    boxBottom = person.boxBottom ?: 0f
                )

                // La vignette fait 88dp quel que soit le nombre de colonnes : c'est
                // elle qui dicte la taille, pas la largeur de la cellule.
                val output = binding.imagePerson.layoutParams.width
                Glide.with(binding.imagePerson)
                    .load(coverPath)
                    // Clé sur le VISAGE de couverture, et rien d'autre : c'est lui,
                    // et lui seul, qui détermine l'image. La clé portait avant le
                    // nombre de photos — donc chaque fusion la faisait changer et
                    // Glide re-décodait toute la grille pour des images inchangées.
                    .signature(ObjectKey(coverFaceId))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    // 565 : une vignette n'a pas de transparence, et c'est deux fois
                    // moins d'octets à décoder puis à ramasser.
                    .format(DecodeFormat.PREFER_RGB_565)
                    .override(
                        FaceCropTransformation.sourceSizeFor(
                            faceData, output, FaceCropTransformation.GRID_MAX_SOURCE
                        )
                    )
                    .placeholder(R.drawable.vector_peoples)
                    .error(R.drawable.vector_peoples)
                    .transform(FaceCropTransformation(faceData, output))
                    .into(binding.imagePerson)
            } else {
                Glide.with(binding.imagePerson)
                    .load(R.drawable.vector_peoples)
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
