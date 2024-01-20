package com.example.photogalleryapp.fragments

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photogalleryapp.R
import com.example.photogalleryapp.adapter.Media
import com.example.photogalleryapp.adapter.MediaAdapter
import com.example.photogalleryapp.databinding.FragmentGalleryBinding
import com.example.photogalleryapp.databinding.FragmentPreviewBinding
import com.example.photogalleryapp.utils.fitSystemWindows
import com.example.photogalleryapp.utils.onWindowInsets
import com.example.photogalleryapp.utils.topMargin

class GalleryFragment : StoreBaseFragment() {

    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var recyclerView: RecyclerView
    override val binding: FragmentGalleryBinding by lazy { FragmentGalleryBinding.inflate(layoutInflater) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaAdapter = MediaAdapter(
            onItemClick = { isVideo, uri -> openMedia(isVideo, uri) },
            onDeleteClick = { isEmpty, uri -> handleDelete(isEmpty, uri) }
        )

        binding.recyclerViewGallery.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = mediaAdapter
        }

        binding.btnBack.setOnClickListener { onBackPressed() }

        // Пример загрузки медиа (замени этот код на свою логику)
        val mediaList = loadMediaFromStorage()
        mediaAdapter.submitList(mediaList)
    }



    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        binding.btnBack.onWindowInsets { view, windowInsets ->
            view.topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
    }

    private fun onBackPressed() {
        view?.let { Navigation.findNavController(it).popBackStack() }
    }

    private fun handleDelete(isEmpty: Boolean, uri: Uri) {
        // Обработка удаления медиа
    }

    private fun openMedia(isVideo: Boolean, uri: Uri) {
    }


    // Этот метод нужно адаптировать под свою логику загрузки медиа
    private fun loadMediaFromStorage(): List<Media> {
        val mediaList = mutableListOf<Media>()

        // Здесь вам нужно добавить свою логику загрузки медиа
        // Например, вы можете использовать ContentResolver для получения медиа из галереи
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val queryUri: Uri = MediaStore.Files.getContentUri("external")

        context?.contentResolver?.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val date = cursor.getLong(dateColumn)
                val data = cursor.getString(dataColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                val uri = ContentUris.withAppendedId(
                    if (mimeType.startsWith("video")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                val isVideo = mimeType.startsWith("video")

                mediaList.add(Media(uri, isVideo, date))
            }
        }

        return mediaList
    }

}
