package com.bwojtowicz.clothescontrol

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode


class MainActivity : AppCompatActivity() {

    private val permissions = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private lateinit var mainToolbar: Toolbar

    private lateinit var capReq: CaptureRequest.Builder
    private lateinit var cameraHandler: Handler
    private lateinit var cameraHandlerThread: HandlerThread
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraTextureView: TextureView
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraDevice: CameraDevice
    private lateinit var blurFilter: BlurFilter
    private lateinit var blurView: ImageView

    private val scanIntervalMs = 1000
    private lateinit var scanningHandlerThread: HandlerThread
    private lateinit var scanningHandler: Handler

    private var processingBarcode = false

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var poseDetector: PoseDetector
    private var blurTimer: CountDownTimer? = null
    private var isBlurred = true
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val blurDelayMs = 15000L
    private val checkIntervalMs = 1000L
    private val humanProximityThreshold = 0.75f // approx half meter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!hasPermissions()) {
            setContentView(R.layout.permissions_request_layout)
            val startButton = findViewById<Button>(R.id.start_app_button)

            startButton.setOnClickListener{
                if(hasPermissions()) {
                    startApp()
                } else {
                    askForPermissions()
                }
            }

            askForPermissions()
        } else {
            startApp()
        }

        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
    }

    private val permissionRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val isGranted = perms.entries.all { it.value }

        if(!isGranted) {
            showPermissionsDialog()
        }
    }

    private fun askForPermissions() {
        permissionRequestLauncher.launch(permissions)
    }

    private fun hasPermissions(): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.permissions_required_dialog_title)
        builder.setMessage(R.string.permissions_required_message)
        builder.setPositiveButton(R.string.grant_permissions) {d, _ ->
            d.cancel()
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        builder.setNegativeButton(R.string.cancel_permissions) {d, _ ->
            d.dismiss()
        }
        builder.show()
    }

    private fun startApp() {
        setContentView(R.layout.activity_main)
        configureToolbar()
        configureCamera()

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "ClothesControl:ScreenLockTag")

        if (!wakeLock!!.isHeld) {
            wakeLock!!.acquire()
        }

        mediaPlayer = MediaPlayer.create(this, R.raw.barcode_scan_sound)
        startPeriodicScanning()
    }

    private fun configureToolbar() {
        mainToolbar = findViewById(R.id.main_toolbar)
        setSupportActionBar(mainToolbar)
        supportActionBar?.title = ""
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openSettings() {
        val dialogView = LayoutInflater.from(this).inflate(
            R.layout.settings_password_dialog,
            null
        )
        val settingsPassword = dialogView.findViewById<EditText>(R.id.settings_password)

        AlertDialog.Builder(this)
            .setTitle(R.string.enter_password)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val password = settingsPassword.text.toString()
                if (isValidPassword(password)) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Toast.makeText(this, R.string.incorrect_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isValidPassword(password: String): Boolean {
        return password == "password"
    }

    private fun configureCamera() {
        cameraTextureView = findViewById(R.id.camera_texture_view)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraHandlerThread = HandlerThread("VideoThread")
        cameraHandlerThread.start()
        cameraHandler = Handler((cameraHandlerThread).looper)
        blurView = findViewById(R.id.blur_view)
        blurFilter = BlurFilter(this)
        isBlurred = true

        cameraTextureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
                startContinuousProcessing()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean { return false }
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        cameraManager.openCamera(cameraManager.cameraIdList[1], object: CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surface = Surface(cameraTextureView.surfaceTexture)
                capReq.addTarget(surface)
                cameraTextureView.rotation = 180F

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {}
                }, cameraHandler)
            }

            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}
        }, cameraHandler)
    }

    private fun startPeriodicScanning() {
        scanningHandlerThread = HandlerThread("ScanningThread")
        scanningHandlerThread.start()
        scanningHandler = Handler((scanningHandlerThread).looper)
        scanningHandler.postDelayed(scanningRunnable, scanIntervalMs.toLong())
    }

    private val scanningRunnable = object : Runnable {
        override fun run() {
            if (!processingBarcode) {
                captureFrameAndScan()
            }
            scanningHandler.postDelayed(this, scanIntervalMs.toLong())
        }
    }

    private val barcodeProcessingActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            processingBarcode = false

            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, R.string.clothing_scanned_successfully, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.clothing_not_scanned, Toast.LENGTH_LONG).show()
            }
        }

    private fun captureFrameAndScan() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build()

        val scanner = BarcodeScanning.getClient(options)
        val image = cameraTextureView.bitmap?.let { InputImage.fromBitmap(it, 0) }

        if (image != null) {
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        processingBarcode = true
                        mediaPlayer.start()

                        val barcodeValue = barcode.displayValue
                        val intent = Intent(this, BarcodeProcessingActivity::class.java)
                        intent.putExtra("barcodeValue", barcodeValue)
                        barcodeProcessingActivityResult.launch(intent)
                        break
                    }
                }
                .addOnFailureListener { _ -> }
        }
    }

    private fun startContinuousProcessing() {
        coroutineScope.launch {
            while (isActive) {
                processFrame()
                delay(33) // ~30 fps
            }
        }
    }

    private suspend fun processFrame() {
        val bitmap = cameraTextureView.bitmap ?: return

        if (isBlurred) {
            blurCameraView(bitmap)
        } else {
            blurView.setImageBitmap(null)
        }

        if (blurTimer == null) {
            detectHuman(bitmap)
        }
    }

    private suspend fun blurCameraView(bitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            val blurredBitmap = blurFilter.multiPassBlur(bitmap, 50f, 2)
            withContext(Dispatchers.Main) {
                blurView.setImageBitmap(blurredBitmap)
            }
        }
    }

    private suspend fun detectHuman(bitmap: Bitmap) {
        if (isHumanPresent(bitmap) && isBlurred) {
            unblurAndStartTimer()
        }
    }

    private fun unblurAndStartTimer() {
        isBlurred = false
        blurTimer?.cancel()
        startBlurTimer()
    }

    private fun startBlurTimer() {
        blurTimer = object : CountDownTimer(blurDelayMs, checkIntervalMs) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                coroutineScope.launch {
                    val bitmap = cameraTextureView.bitmap
                    if (bitmap != null) {
                        if (isHumanPresent(bitmap)) {
                            // Human still present, restart the timer
                            startBlurTimer()
                        } else {
                            // No human detected, apply blur
                            isBlurred = true
                            blurTimer = null
                        }
                    }
                }
            }
        }.start()
    }

    private suspend fun isHumanPresent(bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val result = Tasks.await(poseDetector.process(InputImage.fromBitmap(bitmap, 0)))
                result.allPoseLandmarks.any { landmark ->
                    landmark.inFrameLikelihood > 0.5f &&
                            landmark.position.y / cameraTextureView.height > humanProximityThreshold
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in pose detection", e)
                false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cameraHandlerThread.quit()
        mediaPlayer.release()

        if (wakeLock != null && wakeLock!!.isHeld) {
            wakeLock!!.release()
        }

        blurTimer?.cancel()
        poseDetector.close()
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}