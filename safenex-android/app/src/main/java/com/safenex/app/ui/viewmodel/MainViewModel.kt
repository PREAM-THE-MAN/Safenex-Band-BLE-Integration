package com.safenex.app.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safenex.app.data.audio.AudioRecordingState
import com.safenex.app.data.audio.SafenexAudioRecorder
import com.safenex.app.data.ble.BleManager
import com.safenex.app.data.ble.BleScanner
import com.safenex.app.data.camera.CameraEvidenceState
import com.safenex.app.data.camera.SafenexCameraManager
import com.safenex.app.data.cloud.CloudSyncStatus
import com.safenex.app.data.cloud.SupabaseCloudRepository
import com.safenex.app.data.deterrent.SafenexDeterrentManager
import com.safenex.app.data.dittu.DittuVoiceAssistant
import com.safenex.app.data.dittu.DittuVoiceIntent
import com.safenex.app.data.guardian.AlertDispatcher
import com.safenex.app.data.guardian.Guardian
import com.safenex.app.data.guardian.GuardianAlert
import com.safenex.app.data.guardian.GuardianRepository
import com.safenex.app.data.location.LocationState
import com.safenex.app.data.location.SafenexLocation
import com.safenex.app.data.location.SafenexLocationManager
import com.safenex.app.data.schedule.SafetySchedule
import com.safenex.app.data.schedule.SafenexScheduleManager
import com.safenex.app.data.schedule.ScheduleStatus
import com.safenex.app.data.security.SecurityManager
import com.safenex.app.data.video.SafenexVideoRecorder
import com.safenex.app.data.video.VideoRecordingState
import com.safenex.app.domain.manager.EmergencyManager
import com.safenex.app.domain.manager.EmergencyWakeHelper
import com.safenex.app.domain.model.BleConnectionState
import com.safenex.app.domain.model.EmergencyEvent
import com.safenex.app.domain.model.EmergencyState
import com.safenex.app.domain.model.ScanState
import com.safenex.app.domain.model.TriggerSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates BLE operations, DITTU AI Voice Assistant, Auto-Wake screen launch, Location tracking,
 * GPS breadcrumb cloud streaming, Tactical Deterrent Strobe/Siren, Guardian Alert Dispatch,
 * Scheduled Safety Mode (Commute Guardian), Supabase Cloud sync, Audio, Video, and UI state.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleScanner = BleScanner(application.applicationContext, viewModelScope)
    val bleManager = BleManager(application.applicationContext, viewModelScope)
    val securityManager = SecurityManager(application.applicationContext)
    val locationManager = SafenexLocationManager(application.applicationContext, viewModelScope)
    val guardianRepository = GuardianRepository(application.applicationContext)
    val alertDispatcher = AlertDispatcher(application.applicationContext, guardianRepository, viewModelScope)
    val cloudRepository = SupabaseCloudRepository(viewModelScope)
    val audioRecorder = SafenexAudioRecorder(application.applicationContext, viewModelScope)
    val cameraManager = SafenexCameraManager(application.applicationContext, viewModelScope)
    val videoRecorder = SafenexVideoRecorder(application.applicationContext, viewModelScope)
    val wakeHelper = EmergencyWakeHelper(application.applicationContext)
    val deterrentManager = SafenexDeterrentManager(application.applicationContext, viewModelScope)
    val dittuVoiceAssistant = DittuVoiceAssistant(application.applicationContext, viewModelScope)
    val scheduleManager = SafenexScheduleManager(application.applicationContext, viewModelScope)
    val emergencyManager = EmergencyManager(securityManager, viewModelScope)

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val scanState: StateFlow<ScanState> = bleScanner.scanState
    val emergencyState: StateFlow<EmergencyState> = emergencyManager.emergencyState
    val activeEmergencyEvent: StateFlow<EmergencyEvent?> = emergencyManager.activeEmergencyEvent
    val verificationError: StateFlow<String?> = emergencyManager.verificationError

    // Evidence & Tracking Flows
    val locationState: StateFlow<LocationState> = locationManager.locationState
    val guardians: StateFlow<List<Guardian>> = guardianRepository.guardians
    val alerts: StateFlow<List<GuardianAlert>> = alertDispatcher.alerts
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = cloudRepository.syncStatus
    val streamedFixesCount: StateFlow<Int> = cloudRepository.streamedFixesCount
    val audioRecordingState: StateFlow<AudioRecordingState> = audioRecorder.recordingState
    val cameraEvidenceState: StateFlow<CameraEvidenceState> = cameraManager.cameraState
    val videoRecordingState: StateFlow<VideoRecordingState> = videoRecorder.videoState
    val isDeterrentEnabled: StateFlow<Boolean> = deterrentManager.isDeterrentEnabled
    val isDeterrentActive: StateFlow<Boolean> = deterrentManager.isActive

    // Scheduled Safety Mode Flows
    val scheduleStatus: StateFlow<ScheduleStatus> = scheduleManager.scheduleStatus

    // DITTU AI Voice Assistant Flows
    val isDittuListening: StateFlow<Boolean> = dittuVoiceAssistant.isListening
    val isDittuVoiceWakeEnabled: StateFlow<Boolean> = dittuVoiceAssistant.isVoiceWakeEnabled
    val dittuLastHeardPhrase: StateFlow<String> = dittuVoiceAssistant.lastHeardPhrase
    val dittuAssistantResponse: StateFlow<String> = dittuVoiceAssistant.assistantResponse

    init {
        // Bind BLE characteristic notifications to EmergencyManager
        emergencyManager.bindBleManager(bleManager)
        // Bind all services to EmergencyManager
        emergencyManager.bindServices(
            locationManager,
            alertDispatcher,
            guardianRepository,
            cloudRepository,
            audioRecorder,
            cameraManager,
            videoRecorder,
            wakeHelper,
            deterrentManager
        )

        // Wire Scheduled Safety Manager Escalations & Safe Arrival
        scheduleManager.onEmergencyTriggerRequested = {
            emergencyManager.triggerEmergency(
                EmergencyEvent(
                    source = TriggerSource.SCHEDULE_OVERDUE,
                    rawPayload = "SCHEDULE_60S_TIMEOUT"
                )
            )
        }

        scheduleManager.onSafeArrivalConfirmed = { schedule ->
            if (schedule.notifyGuardianOnArrival) {
                alertDispatcher.dispatchArrivalConfirmation(schedule.destination, schedule.formattedArrivalTime)
            }
            dittuVoiceAssistant.speak("Welcome to ${schedule.destination}! Safe arrival confirmed and guardian updated.")
        }

        // Wire DITTU Voice Intent Dispatcher
        dittuVoiceAssistant.onIntentRecognized = { intent ->
            when (intent) {
                is DittuVoiceIntent.TriggerEmergency -> {
                    emergencyManager.triggerEmergency(
                        EmergencyEvent(source = TriggerSource.VOICE, rawPayload = "DITTU_VOICE_SOS")
                    )
                }
                is DittuVoiceIntent.CallGuardian -> {
                    val callIntent = alertDispatcher.callPrimaryGuardian()
                    if (callIntent != null) {
                        application.startActivity(callIntent)
                    } else {
                        dittuVoiceAssistant.speak("No primary guardian configured yet.")
                    }
                }
                is DittuVoiceIntent.ShareLocationToGuardian -> {
                    val locState = locationManager.locationState.value
                    val loc = (locState as? LocationState.Available)?.location
                    val waIntent = alertDispatcher.createWhatsAppShareIntent(loc)
                    try {
                        application.startActivity(waIntent)
                    } catch (e: Exception) {
                        dittuVoiceAssistant.speak("Unable to open WhatsApp.")
                    }
                }
                is DittuVoiceIntent.QueryLocation -> {
                    val locState = locationManager.locationState.value
                    if (locState is LocationState.Available && !locState.location.address.isNullOrBlank()) {
                        dittuVoiceAssistant.speak("You are currently near ${locState.location.address}")
                    } else {
                        dittuVoiceAssistant.speak("Acquiring GPS satellite position...")
                    }
                }
                is DittuVoiceIntent.QueryBandStatus -> {
                    val isConnected = bleManager.connectionState.value is BleConnectionState.Connected
                    if (isConnected) {
                        dittuVoiceAssistant.speak("SAFENEX Band is connected and active.")
                    } else {
                        dittuVoiceAssistant.speak("SAFENEX Band is not connected.")
                    }
                }
                is DittuVoiceIntent.MuteDeterrent -> {
                    // Refuse mute without PIN verification
                    dittuVoiceAssistant.speak("Emergency alarm is locked. Please verify your PIN to disarm.")
                }
                is DittuVoiceIntent.Unknown -> {
                    // Handled gracefully without disruption
                }
            }
        }

        // Observe scanner: automatically connect when SAFENEX-BAND is found
        viewModelScope.launch {
            scanState.collect { state ->
                if (state is ScanState.DeviceFound) {
                    bleManager.connect(state.device)
                }
            }
        }
    }

    /**
     * Scheduled Safety Mode Actions
     */
    fun startSafetySchedule(
        title: String,
        destination: String,
        arrivalEpochMs: Long,
        extendMinutes: Int = 15,
        notifyGuardian: Boolean = true
    ): SafetySchedule {
        return scheduleManager.startSchedule(title, destination, arrivalEpochMs, extendMinutes, notifyGuardian)
    }

    fun confirmSafeArrival() {
        scheduleManager.confirmSafeArrival()
    }

    fun extendSafetySchedule(extraMinutes: Int = 15) {
        scheduleManager.extendSchedule(extraMinutes)
    }

    fun cancelSafetySchedule() {
        scheduleManager.cancelSchedule()
    }

    fun triggerCheckInNow(schedule: SafetySchedule) {
        scheduleManager.triggerCheckInPrompt(schedule)
    }

    /**
     * DITTU Assistant Controls
     */
    fun toggleDittuVoiceWake(): Boolean {
        return dittuVoiceAssistant.toggleVoiceWake()
    }

    fun setDittuVoiceWake(enabled: Boolean) {
        dittuVoiceAssistant.setVoiceWakeEnabled(enabled)
    }

    fun startDittuListening() {
        dittuVoiceAssistant.startListening()
    }

    fun activateDittuManually() {
        dittuVoiceAssistant.startManualListening()
    }

    fun speakWithDittu(text: String) {
        dittuVoiceAssistant.speak(text)
    }

    /**
     * Starts targeted BLE scan for SAFENEX-BAND.
     */
    fun startScan() {
        bleScanner.startScan()
    }

    /**
     * Cancels active scan.
     */
    fun stopScan() {
        bleScanner.stopScan()
    }

    /**
     * Connects manually to a Bluetooth device.
     */
    fun connectToDevice(device: BluetoothDevice) {
        bleManager.connect(device)
    }

    /**
     * Disconnects from SAFENEX-BAND.
     */
    fun disconnectBand() {
        bleManager.disconnect()
    }

    /**
     * Enters VERIFYING state to unlock/disarm emergency.
     */
    fun startVerification() {
        emergencyManager.startVerification()
    }

    /**
     * Cancels verification and returns to EMERGENCY_ACTIVE.
     */
    fun cancelVerification() {
        emergencyManager.cancelVerification()
    }

    /**
     * Submits PIN for verification.
     */
    fun submitPin(pin: String): Boolean {
        return emergencyManager.submitPinVerification(pin)
    }

    /**
     * Triggers simulated emergency for local verification and testing.
     */
    fun triggerSimulatedEmergency(source: TriggerSource) {
        emergencyManager.triggerSimulatedEmergency(source)
    }

    /**
     * Deterrent Management methods
     */
    fun setDeterrentMode(enabled: Boolean) {
        deterrentManager.setDeterrentMode(enabled)
    }

    fun toggleDeterrentMode(): Boolean {
        return deterrentManager.toggleDeterrentMode()
    }

    fun stopDeterrent() {
        deterrentManager.stopDeterrent()
    }

    /**
     * Guardian Management methods
     */
    fun addGuardian(name: String, phoneNumber: String, relationship: String, isPrimary: Boolean = false) {
        guardianRepository.addGuardian(name, phoneNumber, relationship, isPrimary)
    }

    fun deleteGuardian(guardianId: String) {
        guardianRepository.deleteGuardian(guardianId)
    }

    fun setPrimaryGuardian(guardianId: String) {
        guardianRepository.setPrimaryGuardian(guardianId)
    }

    fun callPrimaryGuardianIntent(): Intent? {
        return alertDispatcher.callPrimaryGuardian()
    }

    fun createWhatsAppShareIntent(): Intent {
        val loc = (locationManager.locationState.value as? LocationState.Available)?.location
        return alertDispatcher.createWhatsAppShareIntent(loc)
    }

    fun shareLocationViaWhatsApp() {
        val intent = createWhatsAppShareIntent()
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            // fallback
        }
    }

    fun openMapsIntent(location: SafenexLocation): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(location.mapsUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
        bleManager.release()
        locationManager.stopTracking()
        audioRecorder.stopRecording()
        videoRecorder.stopRecording()
        deterrentManager.stopDeterrent()
        dittuVoiceAssistant.release()
        wakeHelper.dismissEmergency()
    }
}
