package com.safenex.app.data.video

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust Headless Video Evidence Recorder using Android Camera2 and MediaRecorder.
 * Records continuous MP4 (H.264 + AAC) into app-private protected storage.
 */
class SafenexVideoRecorder(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_VideoRecorder"
        private const val VIDEOS_FOLDER_NAME = "evidence_videos"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var mediaRecorder: MediaRecorder? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentVideoFile: File? = null
    private var recordingStartTime: Long = 0L
    private var tickerJob: Job? = null
    private var isRecording = false

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val _videoState = MutableStateFlow<VideoRecordingState>(VideoRecordingState.Idle)
    val videoState: StateFlow<VideoRecordingState> = _videoState.asStateFlow()

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            val thread = HandlerThread("VideoEvidenceBackgroundThread").apply { start() }
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

    fun hasPermissions(): Boolean {
        val cam = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    /**
     * Starts continuous background video recording.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        if (isRecording || _videoState.value is VideoRecordingState.Recording) {
            Log.w(TAG, "Video recording already active.")
            return currentVideoFile
        }

        if (!hasPermissions()) {
            Log.e(TAG, "Permissions missing for video recording.")
            _videoState.value = VideoRecordingState.Error("Camera & Mic permissions required")
            return null
        }

        val manager = cameraManager ?: return null
        startBackgroundThread()

        try {
            val videosDir = File(context.filesDir, VIDEOS_FOLDER_NAME).apply {
                if (!exists()) mkdirs()
            }
            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val videoFile = File(videosDir, "incident_video_$timestampStr.mp4")
            currentVideoFile = videoFile

            // Find Front Camera (preferred for face capture) or Rear camera
            var targetCameraId: String? = null
            for (id in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    targetCameraId = id
                    break
                }
            }
            if (targetCameraId == null && manager.cameraIdList.isNotEmpty()) {
                targetCameraId = manager.cameraIdList[0]
            }
            val cameraId = targetCameraId ?: return null

            // Determine supported video resolution
            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val videoSizes = map?.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()

            val selectedSize = videoSizes.firstOrNull { it.width == 1280 && it.height == 720 }
                ?: videoSizes.firstOrNull { it.width <= 1280 && it.height <= 720 }
                ?: Size(640, 480)

            Log.i(TAG, "Configuring video recorder: Camera $cameraId, Resolution ${selectedSize.width}x${selectedSize.height}")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFile.absolutePath)
                setVideoEncodingBitRate(2000000) // 2 Mbps
                setVideoFrameRate(30)
                setVideoSize(selectedSize.width, selectedSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                prepare()
            }

            mediaRecorder = recorder
            val recorderSurface = recorder.surface

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera

                    try {
                        camera.createCaptureSession(
                            listOf(recorderSurface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    captureSession = session
                                    try {
                                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                            addTarget(recorderSurface)
                                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                        }
                                        session.setRepeatingRequest(builder.build(), null, backgroundHandler)

                                        recorder.start()
                                        isRecording = true
                                        recordingStartTime = System.currentTimeMillis()

                                        _videoState.value = VideoRecordingState.Recording(
                                            startTime = recordingStartTime,
                                            durationSeconds = 0,
                                            filePath = videoFile.absolutePath
                                        )

                                        // Start duration timer ticker
                                        tickerJob?.cancel()
                                        tickerJob = scope.launch {
                                            while (isActive && isRecording) {
                                                delay(1000)
                                                val elapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                                                _videoState.value = VideoRecordingState.Recording(
                                                    startTime = recordingStartTime,
                                                    durationSeconds = elapsed,
                                                    filePath = videoFile.absolutePath
                                                )
                                            }
                                        }

                                        Log.i(TAG, "Video recording successfully started: ${videoFile.absolutePath}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error starting recorder.start(): ${e.message}", e)
                                        cleanup()
                                        _videoState.value = VideoRecordingState.Error("Video recording failed: ${e.localizedMessage}")
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e(TAG, "Video capture session configuration failed.")
                                    cleanup()
                                    _videoState.value = VideoRecordingState.Error("Session failed")
                                }
                            },
                            backgroundHandler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating capture session: ${e.message}", e)
                        cleanup()
                        _videoState.value = VideoRecordingState.Error("Session error: ${e.localizedMessage}")
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera device disconnected during video recording.")
                    camera.close()
                    cleanup()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                    camera.close()
                    cleanup()
                    _videoState.value = VideoRecordingState.Error("Camera hardware error $error")
                }
            }, backgroundHandler)

            return videoFile
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing video recording: ${e.message}", e)
            cleanup()
            _videoState.value = VideoRecordingState.Error("Init failed: ${e.localizedMessage}")
            return null
        }
    }

    /**
     * Stops and finalizes the active video recording.
     */
    fun stopRecording(): File? {
        isRecording = false
        tickerJob?.cancel()
        val file = currentVideoFile

        try {
            captureSession?.apply {
                try {
                    stopRepeating()
                    abortCaptures()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping capture session: ${e.message}")
                }
                close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing capture session: ${e.message}")
        } finally {
            captureSession = null
        }

        try {
            cameraDevice?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing camera: ${e.message}")
        } finally {
            cameraDevice = null
        }

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during mediaRecorder.stop(): ${e.message}")
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
        }

        stopBackgroundThread()

        if (file != null && file.exists() && file.length() > 0) {
            val totalSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            Log.i(TAG, "Video recording saved successfully: ${file.absolutePath} (${file.length()} bytes, ${totalSeconds}s)")
            _videoState.value = VideoRecordingState.Saved(
                filePath = file.absolutePath,
                durationSeconds = totalSeconds,
                sizeBytes = file.length()
            )
        } else {
            _videoState.value = VideoRecordingState.Idle
        }

        currentVideoFile = null
        return file
    }

    private fun cleanup() {
        isRecording = false
        tickerJob?.cancel()
        try {
            captureSession?.close()
        } catch (e: Exception) {}
        try {
            cameraDevice?.close()
        } catch (e: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {}
        stopBackgroundThread()

        captureSession = null
        cameraDevice = null
        mediaRecorder = null
        currentVideoFile = null
    }

    fun reset() {
        _videoState.value = VideoRecordingState.Idle
    }
}
