package com.example.photogalleryapp.fragments

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import coil.load
import coil.transform.CircleCropTransformation
import com.example.photogalleryapp.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import com.example.photogalleryapp.R
import com.example.photogalleryapp.utils.SwipeDetector
import com.example.photogalleryapp.utils.bottomMargin
import com.example.photogalleryapp.utils.endMargin
import com.example.photogalleryapp.utils.fitSystemWindows
import com.example.photogalleryapp.utils.onWindowInsets
import com.example.photogalleryapp.utils.toggleButton


class PhotoCameraFragment : StoreBaseFragment() {
    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    override val binding: FragmentCameraBinding by lazy { FragmentCameraBinding.inflate(layoutInflater) }
    private var displayId = -1
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            view?.let { view ->
                if (displayId == this@PhotoCameraFragment.displayId) {
                    preview?.targetRotation = view.display.rotation
                    imageCapture?.targetRotation = view.display.rotation
                    imageAnalyzer?.targetRotation = view.display.rotation
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()

        displayManager.registerDisplayListener(displayListener, null)

        binding.run {
            viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
            })

            btnTakePicture.setOnClickListener { takePhoto() }
            btnGallery.setOnClickListener { openPreview() }
            btnSwitchCamera.setOnClickListener { toggleCamera() }

            val swipeGestures = SwipeDetector(
                onLeftSwipe = {},
                onRightSwipe = {
                    Navigation.findNavController(view).navigate(R.id.action_camera_to_video)
                }
            )
            val gestureDetectorCompat = GestureDetector(requireContext(), swipeGestures)
            viewFinder.setOnTouchListener { _, motionEvent ->
                !gestureDetectorCompat.onTouchEvent(motionEvent)
            }
        }
    }

    private fun initViews() {
        adjustInsets()
    }

    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        binding.btnTakePicture.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.bottomMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            }
        }
    }

    private fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
        flag = lensFacing == CameraSelector.DEFAULT_BACK_CAMERA,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_outline_camera_rear,
        secondIcon = R.drawable.ic_outline_camera_front,
    ) {
        lensFacing = if (it) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        startCamera()
    }

    private fun openPreview() {
        if (getMedia().isEmpty()) return
        view?.let { Navigation.findNavController(it).navigate(R.id.action_camera_to_preview) }
    }

    override fun onPermissionGranted() {
        binding.viewFinder.post {
            displayId = binding.viewFinder.display.displayId
            startCamera()

            lifecycleScope.launch(Dispatchers.IO) {
                setLastPhoto()
            }
        }
    }


    private fun setLastPhoto() {
        val lastMedia = getMedia().firstOrNull()

        if (lastMedia != null) {
            setGalleryPhoto(lastMedia.uri)
        } else {
            binding.btnGallery.setImageResource(R.drawable.ic_no_picture)
        }
    }


    private fun startCamera() {
        val viewFinder = binding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val rotation = viewFinder.display.rotation

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(rotation)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                viewLifecycleOwner,
                lensFacing,
                preview,
                imageCapture,
                imageAnalyzer
            )

            preview.setSurfaceProvider(viewFinder.surfaceProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    private fun takePhoto() = lifecycleScope.launch(Dispatchers.Main) {
        captureImage()
    }

    private fun captureImage() {
        val localImageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        // Options for the output image file
        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            OutputFileOptions.Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues
            )
        } else {
            val file = File(outputDirectory, "${System.currentTimeMillis()}.jpg")
            OutputFileOptions.Builder(file)
        }.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            localImageCapture.takePicture(
                outputOptions,
                requireContext().mainExecutor,
                object : OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: OutputFileResults) {
                        outputFileResults.savedUri?.let { uri ->
                            setGalleryPhoto(uri)
                        } ?: setLastPhoto()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        val msg = "Photo capture failed: ${exception.message}"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        exception.printStackTrace()
                    }
                }
            )
        }
    }

    private fun setGalleryPhoto(savedUri: Uri?) {
        binding.btnGallery.load(savedUri) {
            placeholder(R.drawable.ic_no_picture)
            transformations(CircleCropTransformation())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayManager.unregisterDisplayListener(displayListener)
    }
}
