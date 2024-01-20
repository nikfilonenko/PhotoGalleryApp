package com.example.photogalleryapp.fragments

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.View
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.animation.doOnCancel
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import coil.load
import coil.transform.CircleCropTransformation
import com.example.photogalleryapp.R
import com.example.photogalleryapp.databinding.FragmentVideoBinding
import com.example.photogalleryapp.utils.SwipeDetector
import com.example.photogalleryapp.utils.bottomMargin
import com.example.photogalleryapp.utils.endMargin
import com.example.photogalleryapp.utils.fitSystemWindows
import com.example.photogalleryapp.utils.onWindowInsets
import com.example.photogalleryapp.utils.toggleButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@ExperimentalCamera2Interop
class VideoCameraFragment : StoreBaseFragment() {
    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var displayId = -1
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var isRecording = false
    private val animateRecord by lazy {
        ObjectAnimator.ofFloat(binding.btnRecordVideo, View.ALPHA, 1f, 0.5f).apply {
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            doOnCancel { binding.btnRecordVideo.alpha = 1f }
        }
    }

    override val binding: FragmentVideoBinding by lazy { FragmentVideoBinding.inflate(layoutInflater) }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            view?.let { view ->
                if (displayId == this@VideoCameraFragment.displayId) {
                    preview?.targetRotation = view.display.rotation
                    videoCapture?.targetRotation = view.display.rotation
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
            binding.btnRecordVideo.setOnClickListener { recordVideo() }
            btnGallery.setOnClickListener { openGallery() }
            btnSwitchCamera.setOnClickListener { toggleCamera() }

            val swipeGestures = SwipeDetector(
                onLeftSwipe = {Navigation.findNavController(view).navigate(R.id.action_video_to_camera)},
                onRightSwipe = {}
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
        binding.btnRecordVideo.onWindowInsets { view, windowInsets ->
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


    private fun startCamera() {
        val viewFinder = binding.viewFinder
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val localCameraProvider = cameraProviderFuture.get()

            // Configuration for camera preview
            preview = Preview.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .build()

            // Filter camera info for back-facing camera
            val cameraInfo = localCameraProvider.availableCameraInfos.filter {
                Camera2CameraInfo.from(it)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
            }

            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            // Create video capture with recorder
            val recorder = Recorder.Builder()
                .setExecutor(ContextCompat.getMainExecutor(requireContext()))
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            localCameraProvider.unbindAll() // Unbind use-cases before rebinding them

            // Bind all use-cases to the camera with lifecycle
            camera = localCameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                lensFacing,
                preview,
                videoCapture
            )

            // Attach the viewfinder's surface provider to the preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    private fun openGallery() {
        view?.let { Navigation.findNavController(it).navigate(R.id.action_camera_to_gallery) }
    }

    private var recording: Recording? = null

    @SuppressLint("MissingPermission")
    private fun recordVideo() {
        if (isRecording) {
            animateRecord.cancel()
            recording?.stop()
        } else {
            val name = "CosmoFocus-${System.currentTimeMillis()}.mp4"
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
            }
            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                requireContext().contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

            recording = videoCapture?.output?.prepareRecording(requireContext(), mediaStoreOutput)
                ?.withAudioEnabled()
                ?.start(ContextCompat.getMainExecutor(requireContext())) { event ->
                    if (event is VideoRecordEvent.Start) {
                        animateRecord.start()
                    }
                }
        }
        isRecording = !isRecording
    }

    override fun onPermissionGranted() {
        binding.viewFinder.post {
            displayId = binding.viewFinder.display.displayId
            startCamera()
            lifecycleScope.launch(Dispatchers.IO) {
                setLastPhoto()
            }
            camera?.cameraControl
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

    private fun setGalleryPhoto(savedUri: Uri?) {
        binding.btnGallery.load(savedUri) {
            placeholder(R.drawable.ic_no_picture)
            transformations(CircleCropTransformation())
        }
    }
}
