package com.example.photogalleryapp.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.Navigation
import com.example.photogalleryapp.databinding.FragmentVideoPreviewBinding

class VideoPreviewFragment : Fragment() {
    private lateinit var binding: FragmentVideoPreviewBinding
    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideoPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriString = arguments?.getString("mediaUri")
        val mediaUri = Uri.parse(uriString)

        player = ExoPlayer.Builder(requireContext()).build()

        val mediaItem = MediaItem.fromUri(mediaUri)

        binding.btnBack2.setOnClickListener { onBackPressed() }

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
        binding.playerView.player = player
    }

    override fun onPause() {
        super.onPause()
        pause()
    }

    override fun onResume() {
        super.onResume()
        play()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun pause() {
        player?.playWhenReady = false
    }

    private fun play() {
        player?.playWhenReady = true
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun onBackPressed() {
        view?.let { Navigation.findNavController(it).popBackStack() }
    }
}
