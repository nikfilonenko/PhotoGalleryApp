package com.example.photogalleryapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.Navigation
import com.example.photogalleryapp.adapter.MediaAdapter
import com.example.photogalleryapp.databinding.FragmentPreviewBinding
import com.example.photogalleryapp.utils.fitSystemWindows
import com.example.photogalleryapp.utils.onPageSelected
import com.example.photogalleryapp.utils.onWindowInsets
import com.example.photogalleryapp.utils.topMargin


class PreviewMediaFragment : StoreBaseFragment() {
    private val mediaAdapter = MediaAdapter(
        onItemClick = { isVideo, uri ->
            if (!isVideo) {
                val visibility = if (binding.groupPreviewActions.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                binding.groupPreviewActions.visibility = visibility
            } else {
                val play = Intent(Intent.ACTION_VIEW, uri).apply { setDataAndType(uri, "video/mp4") }
                startActivity(play)
            }
        },
        onDeleteClick = { isEmpty, uri ->
            if (isEmpty) onBackPressed()

            val resolver = requireContext().applicationContext.contentResolver
            resolver.delete(uri, null, null)
        },
    )
    private var currentPage = 0
    override val binding: FragmentPreviewBinding by lazy { FragmentPreviewBinding.inflate(layoutInflater) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adjustInsets()

        // Check for the permissions and show files
        if (allPermissionsGranted()) {
            binding.pagerPhotos.apply {
                adapter = mediaAdapter.apply { submitList(getMedia()) }
                onPageSelected { page -> currentPage = page }
            }
        }

        binding.btnBack.setOnClickListener { onBackPressed() }
        binding.btnDelete.setOnClickListener { deleteImage() }
    }

    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        binding.btnBack.onWindowInsets { view, windowInsets ->
            view.topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
    }

    private fun deleteImage() {
        mediaAdapter.deleteImage(currentPage)
    }

    private fun onBackPressed() {
        view?.let { Navigation.findNavController(it).popBackStack() }
    }
}