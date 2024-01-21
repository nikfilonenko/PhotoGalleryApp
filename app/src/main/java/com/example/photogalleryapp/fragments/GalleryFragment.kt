package com.example.photogalleryapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import com.example.photogalleryapp.adapter.MediaAdapter
import com.example.photogalleryapp.databinding.FragmentGalleryBinding

class GalleryFragment : StoreBaseFragment() {
    private val mediaAdapter = MediaAdapter(
        onItemClick = { isVideo, uri ->
            if (!isVideo) {
                val action = GalleryFragmentDirections.actionGalleryToPhotoPreview(uri.toString())
                Navigation.findNavController(requireView()).navigate(action)
            } else {
                val action = GalleryFragmentDirections.actionGalleryToVideoPreview(uri.toString())
                Navigation.findNavController(requireView()).navigate(action)
            }
        },
        onDeleteClick = { isEmpty, uri ->
            if (isEmpty) onBackPressed()

            requireContext().applicationContext.contentResolver.delete(uri, null, null)
        }
    )

    override val binding: FragmentGalleryBinding by lazy { FragmentGalleryBinding.inflate(layoutInflater) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (allPermissionsGranted()) {
            binding.recyclerViewGallery.adapter = mediaAdapter.apply { submitList(getMedia()) }
        }

        binding.recyclerViewGallery.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        binding.btnBack.setOnClickListener { onBackPressed() }
    }


    private fun onBackPressed() {
        view?.let { Navigation.findNavController(it).popBackStack() }
    }
}
