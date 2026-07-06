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

class ModelsAdapter(
    private val onActivate: (AiModel) -> Unit,
    private val onDelete: (AiModel) -> Unit
) : ListAdapter<AiModel, ModelsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAiModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemAiModelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: AiModel) {
            binding.textModelName.text = model.modelName
            binding.textModelInfo.text = buildInfoLine(model)
            // setChecked déclencherait le listener du binding précédent
            binding.radioActive.setOnCheckedChangeListener(null)
            binding.radioActive.isChecked = model.isActive
            binding.radioActive.setOnCheckedChangeListener { _, checked ->
                if (checked && !model.isActive) onActivate(model)
            }
            binding.buttonDelete.setOnClickListener { onDelete(model) }
        }

        private fun buildInfoLine(model: AiModel): String {
            val context = binding.root.context
            val typeLabel = when (model.modelType) {
                AiModel.TYPE_CLIP -> context.getString(R.string.models_type_clip)
                AiModel.TYPE_TRANSLATION -> context.getString(R.string.models_type_translation)
                AiModel.TYPE_CAPTIONING -> context.getString(R.string.models_type_captioning)
                else -> model.modelType
            }
            val parts = mutableListOf(typeLabel)
            model.modelSize?.let { parts.add(it.formatFileSize()) }
            languagesOf(model)?.let { parts.add(it) }
            return parts.joinToString(" · ")
        }

        /** Langues de l'encodeur texte depuis config_json, si présentes. */
        private fun languagesOf(model: AiModel): String? = runCatching {
            model.configJson?.let { ModelConfig.parse(it).languages.joinToString(",") }
        }.getOrNull()
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AiModel>() {
            override fun areItemsTheSame(oldItem: AiModel, newItem: AiModel) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AiModel, newItem: AiModel) =
                oldItem == newItem
        }
    }
}
