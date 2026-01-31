package com.cevague.vindex.ui.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.cevague.vindex.R
import com.cevague.vindex.data.database.dao.PhotoSummary
import com.cevague.vindex.data.database.entity.Photo
import com.cevague.vindex.databinding.ActivityPhotoViewerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private val viewModel: PhotoViewerViewModel by viewModels()
    private lateinit var pagerAdapter: PhotoPagerAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    // État UI
    private var isUiVisible = true

    var isFirstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val list = PhotoViewerNavData.currentList

        if (list.isEmpty()) {
            finish()
            return
        }

        // Récupérer les extras de l'Intent
        val startPosition = intent.getIntExtra(EXTRA_POSITION, 0)

        viewModel.setPhotos(list, startPosition)

        pagerAdapter = PhotoPagerAdapter()
        binding.viewPagerPhotos.adapter = pagerAdapter

        // Configurer le BottomSheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.panelInfo)
        bottomSheetBehavior.apply {
            // Hauteur de la poignée fixe
            peekHeight = (48 * resources.displayMetrics.density).toInt()

            state = BottomSheetBehavior.STATE_COLLAPSED
            isHideable = false
            isDraggable = true
        }

        // Limiter la hauteur max à 60%
        binding.root.post {
            bottomSheetBehavior.maxHeight = (resources.displayMetrics.heightPixels * 0.6).toInt()
        }

        binding.root.post {
            val maxHeight = (resources.displayMetrics.heightPixels * 0.6).toInt()
            bottomSheetBehavior.maxHeight = maxHeight
        }

        // Click sur la poignée : Alterne uniquement entre COLLAPSED et EXPANDED
        binding.dragHandleContainer.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        binding.viewPagerPhotos.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setPosition(position)
            }
        })

        viewModel.photos.observe(this) { photos ->
            pagerAdapter.submitList(photos)

            // Positionner sur la photo cliquée seulement au premier chargement
            if (isFirstLoad && photos.isNotEmpty()) {
                binding.viewPagerPhotos.setCurrentItem(startPosition, false)
                isFirstLoad = false
            }
        }

        viewModel.currentPhoto.observe(this) { photo ->
            photo?.let { updateInfoPanel(it) }
        }

        pagerAdapter.onPhotoTap = {
            toggleUiVisibility()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.panelInfo) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
    }

    private fun toggleUiVisibility() {
        isUiVisible = !isUiVisible

        if (isUiVisible) {
            showSystemUI()
            bottomSheetBehavior.isHideable = false
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            hideSystemUI()
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun updateInfoPanel(photo: Photo) {
        // Date
        binding.textDateAdded.text = photo.dateAdded.let { timestamp ->
            java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } ?: getString(R.string.viewer_no_info_short)

        binding.textDateTake.text = photo.dateTaken?.let { timestamp ->
            java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } ?: getString(R.string.viewer_no_info_short)

        // Dimensions
        binding.textDimensions.text = if (photo.width != null && photo.height != null) {
            "${photo.width} × ${photo.height}"
        } else {
            getString(R.string.viewer_no_info_short)
        }

        // Taille fichier
        binding.textFileSize.text = photo.fileSize?.let { size ->
            android.text.format.Formatter.formatFileSize(this, size)
        } ?: getString(R.string.viewer_no_info_short)

        // Appareil photo (afficher seulement si disponible)
        val cameraInfo = listOfNotNull(photo.cameraMake, photo.cameraModel)
            .joinToString(" ")
            .trim()
        binding.textCamera.text = getString(R.string.viewer_no_info_short)
        if (cameraInfo.isNotEmpty()) {
            binding.textCamera.text = cameraInfo
        }

        // Type d'image (photo, selfie, screenshot, etc.)
        binding.textImageType.text = photo.mediaType

        // Localisation (afficher seulement si disponible, sinon afficher GPS, sinon vide)
        binding.textLocationText.text =
            photo.locationName ?: getString(R.string.viewer_no_info_short)


        if (photo.latitude != null && photo.longitude != null) {
            binding.textLocationCoordinates.text =
                getString(R.string.format_location, photo.latitude, photo.longitude)
        } else {
            binding.textLocationCoordinates.text = getString(R.string.viewer_no_info_short)
        }

        // Chemin du fichier
        binding.textPath.text = photo.fileName
    }

    private fun hideSystemUI() {
        // Utiliser WindowInsetsController (API 30+) ou le compat
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        WindowInsetsControllerCompat(window, binding.root)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_POSITION = "extra_position"

        // Helper pour lancer l'activité proprement
        fun start(context: Context, list: List<PhotoSummary>, position: Int) {
            PhotoViewerNavData.currentList = list

            val intent = Intent(context, PhotoViewerActivity::class.java).apply {
                putExtra(EXTRA_POSITION, position)
            }
            context.startActivity(intent)
        }
    }
}