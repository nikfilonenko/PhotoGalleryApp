package com.example.photogalleryapp.fragments

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.example.photogalleryapp.R
import com.example.photogalleryapp.adapter.Media
import com.google.android.material.snackbar.Snackbar



abstract class BaseFragment : Fragment() {

    abstract val binding: ViewBinding
    protected val outputDirectory: String = "${Environment.DIRECTORY_DCIM}/CosmoFocus/"
    private val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) onPermissionGranted()
        else view?.let {
            Snackbar.make(it, R.string.message_no_permissions, Snackbar.LENGTH_INDEFINITE).setAction(R.string.label_ok) {
                requireActivity().finishAffinity()
            }.show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            onPermissionGranted()
        } else {
            permissionRequest.launch(permissions.toTypedArray())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (allPermissionsGranted()) {
            onPermissionGranted()
        } else {
            permissionRequest.launch(permissions.toTypedArray())
        }
    }

    protected fun getMedia(): List<Media> = getMediaStore()


    private fun getMediaStore(): List<Media> {
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_TAKEN
        )
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN
        )

        val videoItems = queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection, null, null, "${MediaStore.Video.Media.DISPLAY_NAME} ASC")
        val imageItems = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection, null, null, "${MediaStore.Images.Media.DISPLAY_NAME} ASC")

        return videoItems + imageItems
    }


    private fun queryMedia(uri: Uri, projection: Array<String>, selection: String?, selectionArgs: Array<String>?, sortOrder: String): List<Media> {
        val items = mutableListOf<Media>()
        val contentResolver = requireContext().applicationContext.contentResolver

        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)

                val contentUri: Uri = ContentUris.withAppendedId(uri, id)

                if (path == outputDirectory) {
                    items.add(Media(contentUri, uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI, date))
                }
            }
        }

        return items
    }

    protected fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    open fun onPermissionGranted() = Unit
}
