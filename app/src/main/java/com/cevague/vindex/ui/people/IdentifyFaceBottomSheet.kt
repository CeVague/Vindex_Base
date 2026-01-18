package com.cevague.vindex.ui.people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.cevague.vindex.VindexApplication
import com.cevague.vindex.data.database.dao.FaceDao
import com.cevague.vindex.databinding.DialogIdentifyFaceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class IdentifyFaceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogIdentifyFaceBinding? = null
    private val binding get() = _binding!!

    private var pendingFaces: List<FaceDao.FaceWithPhoto> = emptyList()
    private var currentIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogIdentifyFaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadFaces()
    }

    private fun loadFaces() {
        val repository = (requireActivity().application as VindexApplication).personRepository
        lifecycleScope.launch {// On charge la liste une seule fois
            pendingFaces = repository.getPendingFaceWithPhoto()
            currentIndex = 0
            showCurrentFace()
        }
    }


    private fun showCurrentFace() {
        val repository = (requireActivity().application as VindexApplication).personRepository

        if (currentIndex >= pendingFaces.size) {
            dismiss() // Plus rien à afficher
            return
        }

        val faceData = pendingFaces[currentIndex]

        // GLIDE : Utilisation directe de l'objet métier
        Glide.with(this@IdentifyFaceBottomSheet)
            .load(faceData.filePath)
            .transform(FaceCenterCrop(faceData), CircleCrop())
            .override(480, 480)
            .into(binding.imageFace)

        // Gérer la validation du nom
        binding.editName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val name = binding.editName.text.toString().trim()
                if (name.isNotEmpty()) {
                    identifyAs(name, faceData.id) // Utilise faceData.id
                    true
                } else false
            } else false
        }

        binding.buttonSkip.setOnClickListener {
            currentIndex++ // On passe à l'index suivant
            showCurrentFace() // On rafraîchit l'UI
        }

        binding.buttonNotPerson.setOnClickListener {
            lifecycleScope.launch {
                repository.deleteFaceById(faceData.id)
                loadFaces()
            }
        }
    }

    private fun identifyAs(name: String, faceId: Long) {
        val repository = (requireActivity().application as VindexApplication).personRepository
        lifecycleScope.launch {
            // 1. Chercher ou créer la personne
            val personId =
                repository.getPersonByName(name)?.id ?: repository.getOrCreatePersonByName(name)

            // 2. Assigner le visage
            repository.assignFaceToPerson(faceId, personId, "manual", 1.0f, 1.0f)

            // 3. Passer au suivant
            binding.editName.text?.clear()
            currentIndex++
            showCurrentFace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}