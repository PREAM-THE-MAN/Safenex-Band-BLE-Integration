package com.safenex.app.data.audio

/**
 * State representing background audio evidence recording lifecycle.
 */
sealed class AudioRecordingState {
    object Idle : AudioRecordingState()
    data class Recording(
        val startTime: Long,
        val durationSeconds: Int = 0,
        val filePath: String
    ) : AudioRecordingState()
    data class Saved(
        val filePath: String,
        val durationSeconds: Int,
        val sizeBytes: Long
    ) : AudioRecordingState()
    data class Error(val reason: String) : AudioRecordingState()
}
