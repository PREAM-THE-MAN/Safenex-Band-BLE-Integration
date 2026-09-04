package com.safenex.app.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
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
 * High-clarity Blackbox Audio Evidence Recorder.
 * Encodes audio in AAC (.m4a) at 128 kbps, 44.1 kHz inside app-private protected storage.
 */
class SafenexAudioRecorder(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_AudioRecorder"
        private const val AUDIO_FOLDER_NAME = "evidence_audio"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var recordingStartTime: Long = 0L
    private var tickerJob: Job? = null

    private val _recordingState = MutableStateFlow<AudioRecordingState>(AudioRecordingState.Idle)
    val recordingState: StateFlow<AudioRecordingState> = _recordingState.asStateFlow()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts background audio evidence recording.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        if (_recordingState.value is AudioRecordingState.Recording) {
            Log.w(TAG, "Audio recording is already in progress.")
            return currentAudioFile
        }

        if (!hasRecordPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            _recordingState.value = AudioRecordingState.Error("Microphone permission not granted")
            return null
        }

        try {
            val audioDir = File(context.filesDir, AUDIO_FOLDER_NAME).apply {
                if (!exists()) mkdirs()
            }

            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val audioFile = File(audioDir, "incident_$timestampStr.m4a")
            currentAudioFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()

            _recordingState.value = AudioRecordingState.Recording(
                startTime = recordingStartTime,
                durationSeconds = 0,
                filePath = audioFile.absolutePath
            )

            // Start duration counter ticker
            tickerJob?.cancel()
            tickerJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    val elapsedSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                    _recordingState.value = AudioRecordingState.Recording(
                        startTime = recordingStartTime,
                        durationSeconds = elapsedSeconds,
                        filePath = audioFile.absolutePath
                    )
                }
            }

            Log.i(TAG, "Audio evidence recording started: ${audioFile.absolutePath}")
            return audioFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            cleanupRecorder()
            _recordingState.value = AudioRecordingState.Error("Recording failed: ${e.localizedMessage}")
            return null
        }
    }

    /**
     * Stops and finalizes the active audio recording.
     */
    fun stopRecording(): File? {
        tickerJob?.cancel()
        val file = currentAudioFile

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
            Log.e(TAG, "Error releasing MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }

        if (file != null && file.exists() && file.length() > 0) {
            val totalSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            Log.i(TAG, "Audio evidence recording saved: ${file.absolutePath} (${file.length()} bytes, ${totalSeconds}s)")
            _recordingState.value = AudioRecordingState.Saved(
                filePath = file.absolutePath,
                durationSeconds = totalSeconds,
                sizeBytes = file.length()
            )
        } else {
            _recordingState.value = AudioRecordingState.Idle
        }

        currentAudioFile = null
        return file
    }

    private fun cleanupRecorder() {
        tickerJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error in cleanupRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
            currentAudioFile = null
        }
    }
}
