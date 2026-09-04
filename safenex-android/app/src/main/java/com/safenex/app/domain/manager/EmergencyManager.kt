package com.safenex.app.domain.manager

import android.util.Log
import com.safenex.app.data.audio.SafenexAudioRecorder
import com.safenex.app.data.ble.BleManager
import com.safenex.app.data.ble.ProtocolResult
import com.safenex.app.data.ble.SafenexBandProtocol
import com.safenex.app.data.camera.SafenexCameraManager
import com.safenex.app.data.cloud.SupabaseCloudRepository
import com.safenex.app.data.deterrent.SafenexDeterrentManager
import com.safenex.app.data.guardian.AlertDispatcher
import com.safenex.app.data.guardian.GuardianRepository
import com.safenex.app.data.location.LocationState
import com.safenex.app.data.location.SafenexLocationManager
import com.safenex.app.data.security.SecurityManager
import com.safenex.app.data.video.SafenexVideoRecorder
import com.safenex.app.domain.model.EmergencyEvent
import com.safenex.app.domain.model.EmergencyState
import com.safenex.app.domain.model.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * High-reliability Emergency State Machine for the SAFENEX system.
 * Coordinates BLE events, auto-wake/lockscreen launch, high-accuracy GPS tracking,
 * continuous GPS breadcrumb streaming to Supabase, Tactical Strobe/Siren Deterrent,
 * Guardian Alerts, and Continuous Video/Audio Evidence Recording.
 */
class EmergencyManager(
    private val securityManager: SecurityManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "SAFENEX_EmergencyMgr"
    }

    private var locationManager: SafenexLocationManager? = null
    private var alertDispatcher: AlertDispatcher? = null
    private var guardianRepository: GuardianRepository? = null
    private var cloudRepository: SupabaseCloudRepository? = null
    private var audioRecorder: SafenexAudioRecorder? = null
    private var cameraManager: SafenexCameraManager? = null
    private var videoRecorder: SafenexVideoRecorder? = null
    private var wakeHelper: EmergencyWakeHelper? = null
    private var deterrentManager: SafenexDeterrentManager? = null

    private var alertDispatchJob: Job? = null
    private var breadcrumbStreamingJob: Job? = null

    private val _emergencyState = MutableStateFlow(EmergencyState.SAFE)
    val emergencyState: StateFlow<EmergencyState> = _emergencyState.asStateFlow()

    private val _activeEmergencyEvent = MutableStateFlow<EmergencyEvent?>(null)
    val activeEmergencyEvent: StateFlow<EmergencyEvent?> = _activeEmergencyEvent.asStateFlow()

    private val _verificationError = MutableStateFlow<String?>(null)
    val verificationError: StateFlow<String?> = _verificationError.asStateFlow()

    /**
     * Attaches to BleManager streams to process incoming SOS notifications.
     */
    fun bindBleManager(bleManager: BleManager) {
        scope.launch {
            bleManager.characteristicUpdates.collect { (uuid, rawBytes) ->
                when (val result = SafenexBandProtocol.parseNotification(uuid, rawBytes)) {
                    is ProtocolResult.SosEvent -> {
                        Log.w(TAG, "SOS Trigger received via BLE: source=${result.event.source}")
                        triggerEmergency(result.event)
                    }
                    is ProtocolResult.StatusUpdate -> {
                        Log.d(TAG, "Band status update received: ${result.status}")
                    }
                    is ProtocolResult.Ignored -> {
                        Log.d(TAG, "Protocol packet ignored: ${result.reason}")
                    }
                }
            }
        }
    }

    /**
     * Binds Location, Guardian Alert Dispatch, Supabase Cloud, Audio, Camera, Video, Wake, and Deterrent services.
     */
    fun bindServices(
        locMgr: SafenexLocationManager,
        dispatcher: AlertDispatcher,
        guardiansRepo: GuardianRepository,
        cloudRepo: SupabaseCloudRepository,
        recorder: SafenexAudioRecorder,
        camera: SafenexCameraManager,
        video: SafenexVideoRecorder,
        wake: EmergencyWakeHelper,
        deterrent: SafenexDeterrentManager
    ) {
        this.locationManager = locMgr
        this.alertDispatcher = dispatcher
        this.guardianRepository = guardiansRepo
        this.cloudRepository = cloudRepo
        this.audioRecorder = recorder
        this.cameraManager = camera
        this.videoRecorder = video
        this.wakeHelper = wake
        this.deterrentManager = deterrent
    }

    /**
     * Activates Emergency Mode from an SOS event.
     */
    fun triggerEmergency(event: EmergencyEvent) {
        Log.w(TAG, "TRIGGER EMERGENCY ACTIVATED! Source: ${event.source}")
        _activeEmergencyEvent.value = event
        _emergencyState.value = EmergencyState.EMERGENCY_ACTIVE
        _verificationError.value = null

        // 1. Auto-Wake phone screen, bypass lockscreen, and pop open EmergencyScreen
        wakeHelper?.wakeAndLaunchEmergency()

        // 2. Activate Tactical Deterrent (Strobe Flashlight & Acoustic Siren) if enabled
        deterrentManager?.startDeterrent()

        // 3. Start continuous high-accuracy GPS tracking
        locationManager?.startEmergencyTracking()

        // 4. Start Automatic Video Evidence Recording (records continuous HD Video + Audio)
        videoRecorder?.startRecording()

        // 5. Dispatch Emergency Alerts to Guardians & sync incident to Supabase Cloud
        alertDispatchJob?.cancel()
        alertDispatchJob = scope.launch {
            val locState = locationManager?.locationState?.value
            val initialLoc = if (locState is LocationState.Available) locState.location else null
            alertDispatcher?.dispatchAlerts(event, initialLoc)

            // Asynchronously log to Supabase Cloud Database
            val guardiansCount = guardianRepository?.guardians?.value?.size ?: 0
            val eventId = cloudRepository?.insertEmergencyEvent(event, initialLoc, guardiansCount)

            // 6. Start continuous real-time GPS breadcrumb streaming to Supabase
            breadcrumbStreamingJob?.cancel()
            breadcrumbStreamingJob = scope.launch {
                locationManager?.locationState?.collect { state ->
                    if (state is LocationState.Available && _emergencyState.value == EmergencyState.EMERGENCY_ACTIVE) {
                        cloudRepository?.streamGpsBreadcrumb(eventId, state.location)
                    }
                }
            }
        }
    }

    /**
     * Helper to trigger manual/simulated emergency for testing.
     */
    fun triggerSimulatedEmergency(source: TriggerSource) {
        val event = EmergencyEvent(
            source = source,
            timestamp = System.currentTimeMillis(),
            rawPayload = "SIMULATED:${source.name}"
        )
        triggerEmergency(event)
    }

    /**
     * Begins the safety verification flow by entering VERIFYING state.
     */
    fun startVerification() {
        if (_emergencyState.value == EmergencyState.EMERGENCY_ACTIVE) {
            _verificationError.value = null
            _emergencyState.value = EmergencyState.VERIFYING
        }
    }

    /**
     * Cancels verification and returns to EMERGENCY_ACTIVE without disarming.
     */
    fun cancelVerification() {
        if (_emergencyState.value == EmergencyState.VERIFYING) {
            _verificationError.value = null
            _emergencyState.value = EmergencyState.EMERGENCY_ACTIVE
        }
    }

    /**
     * Submits the PIN to verify safety and disarm the emergency session.
     * Transitions: EMERGENCY_ACTIVE -> VERIFYING -> SAFETY_VERIFIED -> SAFE
     */
    fun submitPinVerification(pin: String): Boolean {
        _verificationError.value = null
        val isValid = securityManager.verifyPin(pin)

        if (isValid) {
            Log.i(TAG, "Safety PIN verified successfully.")
            _emergencyState.value = EmergencyState.SAFETY_VERIFIED

            // Immediately silence siren & turn off strobe flashlight
            deterrentManager?.stopDeterrent()

            // Stop continuous breadcrumb streaming
            breadcrumbStreamingJob?.cancel()

            // Stop video & audio recordings and get finalized files
            val recordedVideoFile = videoRecorder?.stopRecording()
            val recordedAudioFile = audioRecorder?.stopRecording()

            scope.launch {
                // Upload recorded video & audio evidence to Supabase Storage in background
                var uploadedVideoUrl: String? = null
                var uploadedAudioUrl: String? = null

                if (recordedVideoFile != null && recordedVideoFile.exists() && recordedVideoFile.length() > 0) {
                    uploadedVideoUrl = cloudRepository?.uploadVideoEvidence(recordedVideoFile)
                }
                if (recordedAudioFile != null && recordedAudioFile.exists() && recordedAudioFile.length() > 0) {
                    uploadedAudioUrl = cloudRepository?.uploadAudioEvidence(recordedAudioFile)
                }

                // Mark emergency incident as RESOLVED with video_url and audio_url in Supabase Cloud
                cloudRepository?.markEmergencyResolved(
                    audioUrl = uploadedAudioUrl,
                    videoUrl = uploadedVideoUrl
                )

                // Stop GPS tracking & clean up alert session
                locationManager?.stopTracking()
                alertDispatcher?.resetAlerts()
                cameraManager?.reset()
                videoRecorder?.reset()
                wakeHelper?.dismissEmergency()

                // Brief pause to display safety verified confirmation
                delay(1200)
                _activeEmergencyEvent.value = null
                _emergencyState.value = EmergencyState.SAFE
                cloudRepository?.reset()
                Log.i(TAG, "Emergency disarmed. State reset to SAFE.")
            }
            return true
        } else {
            Log.w(TAG, "Invalid Safety PIN attempted.")
            _verificationError.value = "Incorrect PIN. Please try again."
            return false
        }
    }

    /**
     * Clears any active verification error messages.
     */
    fun clearVerificationError() {
        _verificationError.value = null
    }
}
