package com.example.camerax

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.*
import android.util.DisplayMetrics
import android.util.Size
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.camerax.base.BaseActivity
import com.example.camerax.constants.AppConstant
import com.example.camerax.constants.AppConstant.Companion.RATIO_16_9_VALUE
import com.example.camerax.constants.AppConstant.Companion.RATIO_4_3_VALUE
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


val permissions = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

class MainActivity : BaseActivity(), View.OnClickListener {
    private var savedUri: Uri? = null
    private var zoomCount: Int = 0
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraControl: CameraControl
    private var cameraInfo: CameraInfo? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED

            ||

            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED

        ) {
            requestPermission()
        } else {
            val mDateFormat =
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            file =
                File(getBatchDirectoryName(), mDateFormat.format(Date()).toString() + ".jpg")
            // Initialize our background executor
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    override fun onResume() {
        super.onResume()
        setUpCamera()
        initViews()
    }

    /**
     * Request all permissions
     */
    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    private fun initViews() {
        ibZoomIn.setOnClickListener(this)
        ibZoomOut.setOnClickListener(this)
        ibCamera.setOnClickListener(this)
        ibCameraSwitch?.setOnClickListener(this)
        ibFlash.setOnClickListener(this)
        photo_view_button.setOnClickListener(this)
    }

    override fun onClick(p0: View) {
        when (p0.id) {
            R.id.ibCamera -> {
                imageCapture?.let { imageCapture ->
                    // Setup image capture metadata
                    var metadata: ImageCapture.Metadata? = null
                    if (metadata == null) {
                        metadata = ImageCapture.Metadata().apply {

                            // Mirror image when using the front camera
                            isReversedHorizontal =
                                lensFacing == CameraSelector.LENS_FACING_FRONT
                        }
                    }

                    val mDateFormat =
                        SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                    file =
                        File(getBatchDirectoryName(), mDateFormat.format(Date()).toString() + ".jpg")

                    // Create output options object which contains file + metadata
                    var outputOptions: ImageCapture.OutputFileOptions? = null
                    if (outputOptions == null) {
                        outputOptions = ImageCapture.OutputFileOptions.Builder(file)
                            .setMetadata(metadata)
                            .build()
                    }

                    // Setup image capture listener which is triggered after photo has been taken
                    imageCapture.takePicture(
                        outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                showToast(R.string.failed_to_capture_photo)
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                                savedUri = output.savedUri ?: Uri.fromFile(file)
                                Handler(Looper.getMainLooper()).post {
                                    showToast("$savedUri" + R.string.image_saved)
                                }
                                // We can only change the foreground Drawable using API level 23+ API
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    // Update the gallery thumbnail with latest picture taken
                                    setGalleryThumbnail(savedUri!!)
                                }
                            }
                        })
                }
            }

            // Changes the flash mode and flash icon when the button is clicked
            R.id.ibFlash -> {
                if (imageCapture?.flashMode == ImageCapture.FLASH_MODE_OFF) {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                    ibFlash.setImageResource(R.drawable.ic_baseline_flash_on)
                } else {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    ibFlash.setImageResource(R.drawable.ic_baseline_flash_off)
                }
            }

            // Changes the lens direction if the button is clicked
            R.id.ibCameraSwitch -> {
                ibCameraSwitch.let {
                    // Disable the button until the camera is set up
                    //    it?.isEnabled = false
                    // Listener for button used to switch cameras. Only called if the button is enabled
                    lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                    // Re-bind use cases to update selected camera
                    bindCameraUseCases()
                }
            }

            R.id.ibZoomIn -> {
                zoomCount += 1
                when (zoomCount) {
                    1 -> cameraControl.setLinearZoom(0.25f)
                    2 -> cameraControl.setLinearZoom(0.5f)
                }
            }
            R.id.ibZoomOut -> {
                when (zoomCount) {
                    1 -> cameraControl.setLinearZoom(0.0001f)
                    2 -> cameraControl.setLinearZoom(0.25f)
                }
                zoomCount -= 1
            }
            R.id.photo_view_button -> {
                if (savedUri != null) {
                    val i = Intent(this, ImageFileViewerActivity::class.java)
                    i.putExtra(AppConstant.URI, savedUri)
                    startActivity(i)
                }
            }
        }
    }


    private fun setGalleryThumbnail(uri: Uri) {
        // Reference of the view that holds the gallery thumbnail
        val thumbnail = findViewById<ImageButton>(R.id.photo_view_button)

        // Run the operations in the view's thread
        thumbnail.post {

            // Remove thumbnail padding
            thumbnail.setPadding(resources.getDimension(R.dimen.xsmall_padding).toInt())

            // Load thumbnail into circular button using Glide
            Glide.with(thumbnail)
                .load(uri)
                .apply(RequestOptions.circleCropTransform())
                .into(thumbnail)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode == 0) {
                // bindCamera()
                initViews()
            } else if (requestCode == 0) {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    /**
     *  [androidx.camera.core.Image Analysis Config] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
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

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        val switchCamerasButton = findViewById<ImageButton>(R.id.ibCameraSwitch)
        try {
            switchCamerasButton.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            switchCamerasButton.isEnabled = false
        }
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { view_finder.display.getRealMetrics(it) }
        // Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        //Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = view_finder.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            //  .setTargetAspectRatio(screenAspectRatio)
            .setTargetResolution(screenSize)
            .setTargetRotation(rotation)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture
            )
            cameraControl = camera!!.cameraControl
            cameraInfo = camera!!.cameraInfo
            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(view_finder.createSurfaceProvider())
        } catch (exc: Exception) {
            showToast(R.string.failed_to_load_camera)
        }
        if (!hasFrontCamera()) {
            ibCameraSwitch.visibility = View.GONE
        }
        if (cameraInfo != null && cameraInfo!!.hasFlashUnit()) {
            ibFlash.visibility = View.VISIBLE
        } else {
            showToast(R.string.front_flash_is_not_available)
            ibFlash.visibility = View.GONE
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Redraw the camera UI controls
        // updateCameraUi()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }

    /**
     * Unused, Used to get the camera count of device
     */
/*    private fun getCameraCount(): Int {
        val manager =
            this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val strings = manager.cameraIdList
            Log.d("CamerId==>", strings.toString())
            strings.size
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            0
        }
    }*/

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
    private fun getBatchDirectoryName(): String? {
        val appFolderPath: String = Environment.getExternalStorageDirectory().toString() + "/images"
        val dir = File(appFolderPath)
        if (!dir.exists() && !dir.mkdirs()) {
            showToast(R.string.could_not_get_storage_location)
        }
        return appFolderPath
    }
}