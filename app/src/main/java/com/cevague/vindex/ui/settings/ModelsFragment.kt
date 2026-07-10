package com.cevague.vindex.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cevague.vindex.R
import com.cevague.vindex.data.database.entity.AiModel
import com.cevague.vindex.data.repository.ModelImportException
import com.cevague.vindex.databinding.FragmentModelsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Gestion des modèles d'IA (sous-page des Paramètres) : liste, import d'un
 * dossier via SAF, activation exclusive par type, suppression.
 */
@AndroidEntryPoint
class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ModelsViewModel by viewModels()

    private lateinit var adapter: ModelsAdapter

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { viewModel.importModel(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ModelsAdapter(
            onActivate = { viewModel.activate(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.recyclerModels.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerModels.adapter = adapter

        binding.buttonImport.setOnClickListener { pickFolder.launch(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.models.collect { models ->
                        adapter.submitList(models)
                        binding.textModelsEmpty.visibility =
                            if (models.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isImporting.collect { importing ->
                        binding.progressImport.visibility =
                            if (importing) View.VISIBLE else View.GONE
                        binding.buttonImport.isEnabled = !importing
                    }
                }
                launch {
                    viewModel.events.collect { event -> showEvent(event) }
                }
            }
        }
    }

    private fun showEvent(event: ModelsViewModel.Event) {
        val message = when (event) {
            is ModelsViewModel.Event.ImportSuccess -> {
                val model = event.model
                // Premier modèle CLIP (auto-activé) : proposer l'indexation, reportable.
                if (model.modelType == AiModel.TYPE_CLIP && model.isActive) {
                    confirmInitialIndexing(model)
                }
                getString(R.string.models_import_success, model.modelName)
            }

            is ModelsViewModel.Event.ImportFailure -> {
                val reasonText = when (event.reason) {
                    ModelImportException.Reason.NO_CONFIG -> getString(R.string.models_error_no_config)
                    ModelImportException.Reason.INVALID_CONFIG -> getString(R.string.models_error_bad_config)
                    ModelImportException.Reason.MISSING_FILE -> getString(R.string.models_error_missing_file)
                    ModelImportException.Reason.INVALID_MODEL -> getString(R.string.models_error_invalid_model)
                    ModelImportException.Reason.ALREADY_EXISTS -> getString(R.string.models_error_exists)
                    ModelImportException.Reason.IO_ERROR -> getString(R.string.models_error_io)
                }
                event.detail?.let { "$reasonText ($it)" } ?: reasonText
            }

            is ModelsViewModel.Event.ConfirmReindex -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.models_reindex_title)
                    .setMessage(R.string.models_reindex_message)
                    .setPositiveButton(R.string.models_reindex_action) { _, _ ->
                        viewModel.requestReindex(event.model)
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
                null
            }
        }
        message?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
    }

    private fun confirmInitialIndexing(model: AiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.models_index_now_title)
            .setMessage(getString(R.string.models_index_now_message, model.modelName))
            .setPositiveButton(R.string.models_index_now_action) { _, _ ->
                viewModel.startInitialIndexing()
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    private fun confirmDelete(model: AiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.models_delete_title)
            .setMessage(getString(R.string.models_delete_message, model.modelName))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(model) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
