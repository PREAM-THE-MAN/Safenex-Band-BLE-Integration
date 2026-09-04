package com.safenex.app.data.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Silent Background Dual-Camera Snapshot Capture Engine using Camera2 API.
 * Captures high-res photos from Front (perpetrator/user) and Rear (surroundings) cameras.
 */
class SafenexCameraManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_CameraMgr"
        private const val PHOTOS_FOLDER_NAME = "evidence_photos"
    }

    private val cameraService: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val _cameraState = MutableStateFlow<CameraEvidenceState>(CameraEvidenceState.Idle)
    val cameraState: StateFlow<CameraEvidenceState> = _cameraState.asStateFlow()

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            val thread = HandlerThread("CameraEvidenceBackgroundThread").apply { start() }
            backgroundThread = thread
            backgroundHandler = Handler(thread.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Background thread interrupted on quit", e)
        } finally {
            backgroundThread = null
            backgroundHandler = null
        }
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sequentially captures silent snapshots from the Front Camera and Rear Camera.
     */
    suspend fun captureDualSnapshots(): Pair<File?, File?> = withContext(Dispatchers.IO) {
        if (!hasCameraPermission()) {
            Log.w(TAG, "Camera permission not granted. Skipping photo capture.")
            _cameraState.value = CameraEvidenceState.Error("Camera permission denied")
            return@withContext Pair(null, null)
        }

        val cameraManager = cameraService ?: return@withContext Pair(null, null)

        _cameraState.value = CameraEvidenceState.Capturing
        startBackgroundThread()

        val photosDir = File(context.filesDir, PHOTOS_FOLDER_NAME).apply {
            if (!exists()) mkdirs()
        }
        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        var frontFile: File? = null
        var backFile: File? = null

        try {
            val cameraIds = cameraManager.cameraIdList
            var frontCameraId: String? = null
            var backCameraId: String? = null

            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT && frontCameraId == null) {
                    frontCameraId = id
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK && backCameraId == null) {
                    backCameraId = id
                }
            }

            // 1. Capture Front Camera Snapshot
            if (frontCameraId != null) {
                val file = File(photosDir, "photo_front_$timestampStr.jpg")
                frontFile = captureSingleCamera(cameraManager, frontCameraId, file)
                Log.i(TAG, "Front camera snapshot result: ${frontFile?.absolutePath} (${frontFile?.length()} bytes)")
            }

            // 2. Capture Rear Camera Snapshot
            if (backCameraId != null) {
                val file = File(photosDir, "photo_back_$timestampStr.jpg")
                backFile = captureSingleCamera(cameraManager, backCameraId, file)
                Log.i(TAG, "Rear camera snapshot result: ${backFile?.absolutePath} (${backFile?.length()} bytes)")
            }

            val totalCaptured = listOfNotNull(frontFile, backFile).size
            _cameraState.value = CameraEvidenceState.Captured(
                frontFile = frontFile,
                backFile = backFile,
                count = totalCaptured
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing dual camera snapshots", e)
            _cameraState.value = CameraEvidenceState.Error("Capture failed: ${e.localizedMessage}")
        } finally {
            stopBackgroundThread()
        }

        return@withContext Pair(frontFile, backFile)
    }

    @SuppressLint("MissingPermission")
    private suspend fun captureSingleCamera(
        manager: CameraManager,
        cameraId: String,
        outputFile: File
    ): File? = suspendCoroutine { continuation ->
        val handler = backgroundHandler ?: run {
            continuation.resume(null)
            return@suspendCoroutine
        }

        try {
            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            // Choose reasonable resolution (e.g. up to 1920x1080 for fast processing)
            val selectedSize = jpegSizes.filter { it.width <= 1920 && it.height <= 1080 }
                .maxByOrNull { it.width * it.height }
                ?: jpegSizes.firstOrNull()
                ?: Size(640, 480)

            val imageReader = ImageReader.newInstance(
                selectedSize.width,
                selectedSize.height,
                ImageFormat.JPEG,
                2
            )

            var isResumed = false
            fun safeResume(file: File?) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(file)
                }
            }

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        FileOutputStream(outputFile).use { fos ->
                            fos.write(bytes)
                        }
                        image.close()
                        safeResume(outputFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving captured JPEG", e)
                        image.close()
                        safeResume(null)
                    }
                } else {
                    safeResume(null)
                }
            }, handler)

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                            addTarget(imageReader.surface)
                                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                        }

                                        session.capture(
                                            captureBuilder.build(),
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult
                                                ) {
                                                    session.close()
                                                    camera.close()
                                                }
                                            },
                                            handler
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error initiating capture on session", e)
                                        camera.close()
                                        safeResume(null)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e(TAG, "Camera session configuration failed for camera $cameraId")
                                    camera.close()
                                    safeResume(null)
                                }
                            },
                            handler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating capture session for camera $cameraId", e)
                        camera.close()
                        safeResume(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    safeResume(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error on camera $cameraId")
                    camera.close()
                    safeResume(null)
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Exception opening camera $cameraId", e)
            continuation.resume(null)
        }
    }

    fun reset() {
        _cameraState.value = CameraEvidenceState.Idle
    }
}
