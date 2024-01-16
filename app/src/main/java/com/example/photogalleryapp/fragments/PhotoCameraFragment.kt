package com.example.photogalleryapp.fragments

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import coil.load
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.photogalleryapp.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.example.photogalleryapp.R
import com.example.photogalleryapp.utils.SwipeGestureDetector
import com.example.photogalleryapp.utils.bottomMargin
import com.example.photogalleryapp.utils.endMargin
import com.example.photogalleryapp.utils.fitSystemWindows
import com.example.photogalleryapp.utils.onWindowInsets
import com.example.photogalleryapp.utils.startPadding
import com.example.photogalleryapp.utils.toggleButton
import com.example.photogalleryapp.utils.topPadding


class PhotoCameraFragment : BaseFragment<FragmentCameraBinding>(R.layout.fragment_camera) {
    // An instance for display manager to get display change callbacks
    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // A lazy instance of the current fragment's view binding
    override val binding: FragmentCameraBinding by lazy { FragmentCameraBinding.inflate(layoutInflater) }

    private var displayId = -1

    // Selector showing which camera is selected (front or back)
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var hdrCameraSelector: CameraSelector? = null

    /**
     * A display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@PhotoCameraFragment.displayId) {
                preview?.targetRotation = view.display.rotation
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

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

            btnTakePicture.setOnClickListener { takePicture() }
            btnGallery.setOnClickListener { openPreview() }
            btnSwitchCamera.setOnClickListener { toggleCamera() }
            flExposure.setOnClickListener { flExposure.visibility = View.GONE }

            // This swipe gesture adds a fun gesture to switch between video and photo
            val swipeGestures = SwipeGestureDetector().apply {
                setSwipeCallback(right = {
                    Navigation.findNavController(view).navigate(R.id.action_camera_to_video)
                })
            }
            val gestureDetectorCompat = GestureDetector(requireContext(), swipeGestures)
            viewFinder.setOnTouchListener { _, motionEvent ->
                if (gestureDetectorCompat.onTouchEvent(motionEvent)) return@setOnTouchListener false
                return@setOnTouchListener true
            }
        }
    }

    /**
     * Create some initial states
     * */
    private fun initViews() {
        adjustInsets()
    }

    /**
     * This methods adds all necessary margins to some views based on window insets and screen orientation
     * */
    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        binding.btnTakePicture.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.bottomMargin =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            }
        }
        binding.llTimerOptions.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.topPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            } else {
                view.startPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left
            }
        }
        binding.llFlashOptions.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.topPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            } else {
                view.startPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left
            }
        }
    }

    /**
     * Change the facing of camera
     *  toggleButton() function is an Extension function made to animate button rotation
     * */
    fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
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

    /**
     * Navigate to PreviewFragment
     * */
    private fun openPreview() {
        if (getMedia().isEmpty()) return
        view?.let { Navigation.findNavController(it).navigate(R.id.action_camera_to_preview) }
    }


    override fun onPermissionGranted() {
        // Each time apps is coming to foreground the need permission check is being processed
        binding.viewFinder.let { vf ->
            vf.post {
                // Setting current display ID
                displayId = vf.display.displayId
                startCamera()
                lifecycleScope.launch(Dispatchers.IO) {
                    // Do on IO Dispatcher
                    setLastPictureThumbnail()
                }
            }
        }
    }

    private fun setLastPictureThumbnail() = binding.btnGallery.post {
        getMedia().firstOrNull() // check if there are any photos or videos in the app directory
            ?.let { setGalleryThumbnail(it.uri) } // preview the last one
            ?: binding.btnGallery.setImageResource(R.drawable.ic_no_picture) // or the default placeholder
    }

    /**
     * Unbinds all the lifecycles from CameraX, then creates new with new parameters
     * */
    private fun startCamera() {
        // This is the CameraX PreviewView where the camera will be rendered
        val viewFinder = binding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: InterruptedException) {
                Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            } catch (e: ExecutionException) {
                Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            }

            // The display information
            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            // The ratio for the output image and preview
            val aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
            // The display rotation
            val rotation = viewFinder.display.rotation

            val localCameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

            // The Configuration of camera preview
            preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio) // set the camera aspect ratio
                .setTargetRotation(rotation) // set the camera rotation
                .build()

            // The Configuration of image capture
            imageCapture = Builder()
                .setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY) // setting to have pictures with highest quality possible (may be slow)
                .setTargetAspectRatio(aspectRatio) // set the capture aspect ratio
                .setTargetRotation(rotation) // set the capture rotation
                .build()

            // The Configuration of image analyzing
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio) // set the analyzer aspect ratio
                .setTargetRotation(rotation) // set the analyzer rotation
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // in our analysis, we care about the latest image
                .build()

            // Unbind the use-cases before rebinding them
            localCameraProvider.unbindAll()
            // Bind all use cases to the camera with lifecycle
            bindToLifecycle(localCameraProvider, viewFinder)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindToLifecycle(localCameraProvider: ProcessCameraProvider, viewFinder: PreviewView) {
        try {
            localCameraProvider.bindToLifecycle(
                viewLifecycleOwner, // current lifecycle owner
                hdrCameraSelector ?: lensFacing, // either front or back facing
                preview, // camera preview use case
                imageCapture, // image capture use case
                imageAnalyzer, // image analyzer use case
            ).run {
                // Init camera exposure control
                cameraInfo.exposureState.run {
                    val lower = exposureCompensationRange.lower
                    val upper = exposureCompensationRange.upper

                    binding.sliderExposure.run {
                        valueFrom = lower.toFloat()
                        valueTo = upper.toFloat()
                        stepSize = 1f
                        value = exposureCompensationIndex.toFloat()

                        addOnChangeListener { _, value, _ ->
                            cameraControl.setExposureCompensationIndex(value.toInt())
                        }
                    }
                }
            }

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind use cases", e)
        }
    }

    /**
     *  Detecting the most suitable aspect ratio for current dimensions
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }


    private fun takePicture() = lifecycleScope.launch(Dispatchers.Main) {
        captureImage()
    }

    private fun captureImage() {
        val localImageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        // Setup image capture metadata
        val metadata = Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA
        }
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Options fot the output image file
        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            val contentResolver = requireContext().contentResolver

            // Create the output uri
            val contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            OutputFileOptions.Builder(contentResolver, contentUri, contentValues)
        } else {
            File(outputDirectory).mkdirs()
            val file = File(outputDirectory, "${System.currentTimeMillis()}.jpg")

            OutputFileOptions.Builder(file)
        }.setMetadata(metadata).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            localImageCapture.takePicture(
                outputOptions, // the options needed for the final image
                requireContext().mainExecutor, // the executor, on which the task will run
                object : OnImageSavedCallback { // the callback, about the result of capture process
                    override fun onImageSaved(outputFileResults: OutputFileResults) {
                        // This function is called if capture is successfully completed
                        outputFileResults.savedUri
                            ?.let { uri ->
                                setGalleryThumbnail(uri)
                                Log.d(TAG, "Photo saved in $uri")
                            }
                            ?: setLastPictureThumbnail()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        // This function is called if there is an errors during capture process
                        val msg = "Photo capture failed: ${exception.message}"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        Log.e(TAG, msg)
                        exception.printStackTrace()
                    }
                }
            )
        }
    }

    private fun setGalleryThumbnail(savedUri: Uri?) = binding.btnGallery.load(savedUri) {
        placeholder(R.drawable.ic_no_picture)
        transformations(CircleCropTransformation())
        listener(object : ImageRequest.Listener {
            override fun onError(request: ImageRequest, result: ErrorResult) {
                super.onError(request, result)
                binding.btnGallery.load(savedUri) {
                    placeholder(R.drawable.ic_no_picture)
                    transformations(CircleCropTransformation())
//                    fetcher(VideoFrameUriFetcher(requireContext()))
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onBackPressed() = when {
        else -> requireActivity().finish()
    }

    companion object {
        private const val TAG = "CameraXDemo"

        const val KEY_FLASH = "sPrefFlashCamera"
        const val KEY_GRID = "sPrefGridCamera"
        const val KEY_HDR = "sPrefHDR"

        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }
}
