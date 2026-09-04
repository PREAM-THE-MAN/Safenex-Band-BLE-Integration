package com.safenex.app.data.deterrent

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Manages the Tactical Deterrent Engine:
 * - High-Frequency Camera LED Torch Strobe (disorienting tactical flash).
 * - High-Decibel Acoustic Siren Alarm (frequency sweep synthesizer).
 * - Holds Partial WakeLock so Alarm & Strobe continue non-stop even when Power Button is pressed.
 * - Protection Mode (Audible Deterrent vs Silent Stealth).
 */
class SafenexDeterrentManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "SAFENEX_Deterrent"
        private const val PREFS_NAME = "safenex_deterrent_prefs"
        private const val KEY_DETERRENT_ENABLED = "key_deterrent_enabled"
        private const val SAMPLE_RATE = 44100
        private const val STROBE_INTERVAL_MS = 100L // 100ms tactical pulse
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    private val _isDeterrentEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DETERRENT_ENABLED, true)
    )
    val isDeterrentEnabled: StateFlow<Boolean> = _isDeterrentEnabled.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var strobeJob: Job? = null
    private var sirenJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var torchCameraId: String? = null

    init {
        findTorchCameraId()
    }

    private fun findTorchCameraId() {
        try {
            val manager = cameraManager ?: return
            for (id in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    break
                }
            }
            if (torchCameraId == null && manager.cameraIdList.isNotEmpty()) {
                torchCameraId = manager.cameraIdList.firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding flash camera ID: ${e.message}")
        }
    }

    fun setDeterrentMode(enabled: Boolean) {
        _isDeterrentEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DETERRENT_ENABLED, enabled).apply()
        Log.i(TAG, "Deterrent Mode updated: enabled=$enabled")
    }

    fun toggleDeterrentMode(): Boolean {
        val newMode = !_isDeterrentEnabled.value
        setDeterrentMode(newMode)
        return newMode
    }

    /**
     * Activates the Strobe Torch and Acoustic Siren if Deterrent Mode is enabled.
     * Holds a Partial WakeLock so CPU never sleeps during active emergencies.
     */
    @SuppressLint("WakelockTimeout")
    fun startDeterrent() {
        if (!_isDeterrentEnabled.value) {
            Log.i(TAG, "Deterrent Mode is OFF (Silent Stealth active). Skipping strobe and siren.")
            return
        }

        if (_isActive.value) return
        _isActive.value = true

        Log.w(TAG, "ACTIVATING TACTICAL DETERRENT: Strobe & Siren initiated with WakeLock!")

        try {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "safenex:DeterrentWakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
                Log.d(TAG, "Deterrent Partial WakeLock acquired.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }

        startStrobe()
        startSiren()
    }

    /**
     * Immediately silences the alarm and turns off the torch.
     * Releases the background WakeLock.
     */
    fun stopDeterrent() {
        _isActive.value = false
        stopStrobe()
        stopSiren()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Deterrent Partial WakeLock released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }

        Log.i(TAG, "Tactical Deterrent deactivated.")
    }

    private fun startStrobe() {
        strobeJob?.cancel()
        val cameraId = torchCameraId ?: return
        val manager = cameraManager ?: return

        strobeJob = scope.launch(Dispatchers.IO) {
            var torchOn = false
            try {
                while (this.isActive && _isActive.value) {
                    torchOn = !torchOn
                    try {
                        manager.setTorchMode(cameraId, torchOn)
                    } catch (e: Exception) {
                        Log.w(TAG, "Torch mode error: ${e.message}")
                    }
                    delay(STROBE_INTERVAL_MS)
                }
            } finally {
                try {
                    manager.setTorchMode(cameraId, false)
                } catch (e: Exception) {}
            }
        }
    }

    private fun stopStrobe() {
        strobeJob?.cancel()
        strobeJob = null
        torchCameraId?.let { id ->
            try {
                cameraManager?.setTorchMode(id, false)
            } catch (e: Exception) {}
        }
    }

    private fun startSiren() {
        sirenJob?.cancel()
        sirenJob = scope.launch(Dispatchers.Default) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                audioManager?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack = track
            track.play()

            val buffer = ShortArray(1024)
            var phase = 0.0
            var currentFreq = 800.0
            var freqIncreasing = true

            try {
                while (this.isActive && _isActive.value) {
                    for (i in buffer.indices) {
                        // Sweep frequency between 800 Hz and 1300 Hz
                        if (freqIncreasing) {
                            currentFreq += 0.05
                            if (currentFreq >= 1300.0) freqIncreasing = false
                        } else {
                            currentFreq -= 0.05
                            if (currentFreq <= 800.0) freqIncreasing = true
                        }

                        val sample = (sin(phase) * Short.MAX_VALUE * 0.85).toInt().toShort()
                        buffer[i] = sample
                        phase += 2.0 * Math.PI * currentFreq / SAMPLE_RATE
                        if (phase >= 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI
                        }
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Siren synthesis loop ended: ${e.message}")
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {}
                audioTrack = null
            }
        }
    }

    private fun stopSiren() {
        sirenJob?.cancel()
        sirenJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }
}
