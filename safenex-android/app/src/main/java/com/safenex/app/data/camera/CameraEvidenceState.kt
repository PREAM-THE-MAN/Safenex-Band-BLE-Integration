package com.safenex.app.data.camera

import java.io.File

/**
 * State representing background dual-camera snapshot evidence capture.
 */
sealed class CameraEvidenceState {
    object Idle : CameraEvidenceState()
    object Capturing : CameraEvidenceState()
    data class Captured(
        val frontFile: File?,
        val backFile: File?,
        val count: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : CameraEvidenceState()
    data class Error(val reason: String) : CameraEvidenceState()
}
