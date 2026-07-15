package com.cevague.vindex.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cevague.vindex.R
import com.cevague.vindex.ai.ModelConfig
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.databinding.ItemAiModelBinding
import com.cevague.vindex.databinding.ItemAlbumHeaderBinding

/** Liste des modèles, sectionnée par type (cf. [ModelListItem]). */
class ModelsAdapter(
    private val onActivate: (AiModel) -> Unit,
    private val onDelete: (AiModel) -> Unit
) : ListAdapter<ModelListItem, RecyclerView.ViewHolder>(DIFF) {

    class HeaderViewHolder(val binding: ItemAlbumHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    class ModelViewHolder(val binding: ItemAiModelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ModelListItem.Header -> VIEW_TYPE_HEADER
        is ModelListItem.Model -> VIEW_TYPE_MODEL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemAlbumHeaderBinding.inflate(inflater, parent, false))
        } else {
            ModelViewHolder(ItemAiModelBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ModelListItem.Header ->
                (holder as HeaderViewHolder).binding.textHeader.text = item.title

            is ModelListItem.Model -> bindModel(holder as ModelViewHolder, item.model)
        }
    }

    private fun bindModel(holder: ModelViewHolder, model: AiModel) {
        holder.binding.textModelName.text = model.modelName
        holder.binding.textModelInfo.text = buildInfoLine(holder, model)
        // Le radio n'est qu'un indicateur : il reflète toujours isActive.
        // L'activation (potentiellement différée par une confirmation) part
        // du clic sur la ligne, jamais d'un toggle optimiste du widget — sans
        // quoi un refus laisserait deux modèles cochés (la liste ne re-émet pas).
        holder.binding.radioActive.isChecked = model.isActive
        holder.binding.root.setOnClickListener {
            if (!model.isActive) onActivate(model)
        }
        holder.binding.buttonDelete.setOnClickListener { onDelete(model) }
    }

    /** Le type reste dans la ligne malgré l'en-tête : la liste se lit aussi ligne à ligne. */
    private fun buildInfoLine(holder: ModelViewHolder, model: AiModel): String {
        val context = holder.itemView.context
        val parts = mutableListOf(context.getString(typeLabelOf(model.modelType)))
        model.modelSize?.let { parts.add(it.formatFileSize()) }
        languagesOf(model)?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    /** Langues de l'encodeur texte depuis config_json, si présentes. */
    private fun languagesOf(model: AiModel): String? = runCatching {
        model.configJson?.let { ModelConfig.parse(it).languages.joinToString(",") }
    }.getOrNull()

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_MODEL = 1

        /**
         * Ordre des sections : la recherche d'abord (elle sert à tout le monde), puis
         * la chaîne visages dans l'ordre où elle s'exécute — détecter, puis reconnaître.
         */
        val TYPE_ORDER = listOf(
            AiModel.TYPE_CLIP,
            AiModel.TYPE_FACE_DETECTION,
            AiModel.TYPE_FACE_EMBEDDING,
            AiModel.TYPE_TRANSLATION,
            AiModel.TYPE_CAPTIONING
        )

        /** Titre de section : court, il nomme le rôle plutôt que la technique. */
        fun sectionLabelOf(modelType: String): Int = when (modelType) {
            AiModel.TYPE_CLIP -> R.string.models_section_clip
            AiModel.TYPE_FACE_DETECTION -> R.string.models_section_face_detection
            AiModel.TYPE_FACE_EMBEDDING -> R.string.models_section_face_embedding
            AiModel.TYPE_TRANSLATION -> R.string.models_section_translation
            AiModel.TYPE_CAPTIONING -> R.string.models_section_captioning
            else -> R.string.models_section_other
        }

        fun typeLabelOf(modelType: String): Int = when (modelType) {
            AiModel.TYPE_CLIP -> R.string.models_type_clip
            AiModel.TYPE_FACE_DETECTION -> R.string.models_type_face_detection
            AiModel.TYPE_FACE_EMBEDDING -> R.string.models_type_face_embedding
            AiModel.TYPE_TRANSLATION -> R.string.models_type_translation
            AiModel.TYPE_CAPTIONING -> R.string.models_type_captioning
            else -> R.string.models_type_unknown
        }

        private val DIFF = object : DiffUtil.ItemCallback<ModelListItem>() {
            override fun areItemsTheSame(oldItem: ModelListItem, newItem: ModelListItem) =
                when {
                    oldItem is ModelListItem.Header && newItem is ModelListItem.Header ->
                        oldItem.title == newItem.title

                    oldItem is ModelListItem.Model && newItem is ModelListItem.Model ->
                        oldItem.model.id == newItem.model.id

                    else -> false
                }

            override fun areContentsTheSame(oldItem: ModelListItem, newItem: ModelListItem) =
                oldItem == newItem
        }
    }
}
