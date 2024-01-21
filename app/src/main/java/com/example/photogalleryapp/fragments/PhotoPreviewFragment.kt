package com.example.photogalleryapp.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.photogalleryapp.databinding.FragmentPhotoPreviewBinding

class PhotoPreviewFragment : Fragment() {
    private lateinit var binding: FragmentPhotoPreviewBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPhotoPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriString = arguments?.getString("mediaUri")
        val mediaUri = Uri.parse(uriString)

        binding.btnBack2.setOnClickListener { onBackPressed() }

        binding.imageView.setImageURI(Uri.parse(mediaUri.toString()))
    }


    private fun onBackPressed() {
        view?.let { Navigation.findNavController(it).popBackStack() }
    }
}
